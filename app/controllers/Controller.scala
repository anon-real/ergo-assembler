package controllers

import akka.actor.ActorSystem
import dao.{AssemblyReqDAO, ReqSummaryDAO}
import io.circe.Json
import io.circe.syntax._
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
      if (!Conf.functioning || !Conf.functioningAdmin) throw new Exception("Assembler is not functioning currently")
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
      if (!Conf.functioning || !Conf.functioningAdmin) throw new Exception("Assembler is not functioning currently")
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
      Ok(nodeService.returnFunds(mine, address).asJson).as("application/json")
    } catch {
      case e: Exception =>
        errorResponse(e)
    }
  }

  def stop(apiKey: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      if (apiKey == Conf.activeNodeApi) {
        Conf.functioningAdmin = false
        Ok("Stopped functioning")
      } else throw new Exception("Wrong pass")
    } catch {
      case e: Exception =>
        errorResponse(e)
    }
  }

  def start(apiKey: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      if (apiKey == Conf.activeNodeApi) {
        Conf.functioningAdmin = true
        Ok("Started functioning")
      } else throw new Exception("Wrong pass")
    } catch {
      case e: Exception =>
        errorResponse(e)
    }
  }

  def ignoreTime(apiKey: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      if (apiKey == Conf.activeNodeApi) {
        Conf.ignoreTime = true
        Ok("Stopped considering time for requests")
      } else throw new Exception("Wrong pass")
    } catch {
      case e: Exception =>
        errorResponse(e)
    }
  }

  def considerTime(apiKey: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      if (apiKey == Conf.activeNodeApi) {
        Conf.ignoreTime = false
        Ok("Time is now being considered for requests")
      } else throw new Exception("Wrong pass")
    } catch {
      case e: Exception =>
        errorResponse(e)
    }
  }

  def state(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      Ok(
        s"""{
          |  "functioning": ${Conf.functioning},
          |  "functioningAdmin": ${Conf.functioningAdmin},
          |  "activeNode": "${Conf.activeNodeUrl}",
          |  "ignoreTime": ${Conf.ignoreTime}
          |}""".stripMargin).as("application/json")
    } catch {
      case e: Exception =>
        errorResponse(e)
    }
  }
}
