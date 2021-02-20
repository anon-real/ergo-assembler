package controllers

import akka.actor.ActorSystem
import dao.{AssemblyReqDAO, ReqSummaryDAO}
import io.circe.Json
import javax.inject._
import models.{Assembly, Summary}
import play.api.Logger
import play.api.libs.circe.Circe
import play.api.mvc._
import scalaj.http.Http
import services.NodeService
import utils.Conf

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class Controller @Inject()(cc: ControllerComponents, actorSystem: ActorSystem,
                           reqSummaryDAO: ReqSummaryDAO, assemblyReqDAO: AssemblyReqDAO, nodeService: NodeService)
                          (implicit exec: ExecutionContext)
  extends AbstractController(cc) with Circe {
  private val defaultHeader: Seq[(String, String)] = Seq[(String, String)](("Content-Type", "application/json"))

  private val logger: Logger = Logger(this.getClass)

  def errorResponse(e: Exception): Result = {
    val msg = e.getMessage.replaceAll("\n", "\\\\n").replaceAll("\"", "\\\\\"")
    BadRequest(
      s"""{
         |  "success": false,
         |  "detail": "$msg"
         |}""".stripMargin).as("application/json")
  }

  def follow: Action[Json] = Action(circe.json).async { implicit request =>
    try {
      val req = Assembly(request.body)
      req.scanId = nodeService.registerScan(req.address)
      val summary = Summary(req)
      val cur = assemblyReqDAO.insert(req) map (_ => {
        reqSummaryDAO.insert(summary) map (_ => {
          logger.info(s"registered ${req.id} - ${req.scanId}")
          Ok(
            s"""{
               |  "id": "${req.id}",
               |  "dueTime": ${Conf.followRequestFor}
               |}""".stripMargin
          ).as("application/json")

        }) recover {
          case e: Exception => errorResponse(e)
        }
      }) recover {
        case e: Exception => Future {
          errorResponse(e)
        }
      }
      cur.flatten
    } catch {
      case e: Exception =>
        Future {
          errorResponse(e)
        }
    }
  }

  def result(id: String): Action[AnyContent] = Action.async { implicit request =>
    try {
      reqSummaryDAO.byId(id) map (res => {
        Ok(
          s"""{
             |  "id": "$id",
             |  "tx": ${res.tx.orNull},
             |  "detail": "${res.details}"
             |}""".stripMargin).as("application/json")
      }) recover {
        case e: Exception => errorResponse(e)
      }
    } catch {
      case e: Exception =>
        Future {
          errorResponse(e)
        }
    }
  }

  def compile: Action[Json] = Action(circe.json) { implicit request =>
    try {
      val script = request.body.as[String].getOrElse("")
      Ok(
        s"""{
           |  "address": "${nodeService.compile(script)}"
           |}""".stripMargin).as("application/json")
    } catch {
      case e: Exception => errorResponse(e)
    }
  }

  def returnTx(mine: String, address: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>

    try {
      val res = Http(s"https://api.ergoplatform.com/api/v0/transactions/boxes/byAddress/unspent/${address}").headers(defaultHeader).asString
      val boxes = io.circe.parser.parse(res.body).getOrElse(Json.Null).as[Seq[Json]].getOrElse(Seq())
        .map(box => box.hcursor.downField("id").as[String].getOrElse(throw new Exception("wrong format")))
        .map(id => nodeService.getUnspentBox(id))
      val tx = nodeService.sendBoxesTo(boxes, mine)
      val ok = tx.hcursor.keys.getOrElse(Seq()).exists(key => key == "id")
      if (ok) {
        nodeService.broadcastTx(tx.noSpaces)
        Ok(tx.hcursor.downField("id").as[String].getOrElse("")).as("application/json")
      }
      else throw new Exception(s"Could not generate tx, ${tx.noSpaces}")
    } catch {
      case e: Exception =>
        errorResponse(e)
    }
  }
}
