package controllers

import akka.actor.ActorSystem
import dao.{AssemblyReqDAO, ReqSummaryDAO}
import io.circe.Json
import javax.inject._
import models.{Assembly, Summary}
import play.api.Logger
import play.api.libs.circe.Circe
import play.api.mvc._
import services.NodeService

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class Controller @Inject()(cc: ControllerComponents, actorSystem: ActorSystem,
                           reqSummaryDAO: ReqSummaryDAO, assemblyReqDAO: AssemblyReqDAO, nodeService: NodeService)
                          (implicit exec: ExecutionContext)
  extends AbstractController(cc) with Circe {

  private val logger: Logger = Logger(this.getClass)

  def errorResponse(e: Exception): Result = {
    BadRequest(
      s"""{
         |  "success": false,
         |  "detail": "${e.getMessage}"
         |}""".stripMargin).as("application/json")
  }

  def follow: Action[Json] = Action(circe.json).async { implicit request =>
    try {
      val req = Assembly(request.body)
//      req.scanId = nodeService.registerScan(req.address)
      req.scanId = 16
      val summary = Summary(req)
      val cur = assemblyReqDAO.insert(req) map (_ => {
        reqSummaryDAO.insert(summary) map (_ => {
          Ok(
            s"""{
               |  "id": "${req.id}"
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
             |  "txId": ${res.tx.orNull},
             |  "details": "${res.details}"
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
}
