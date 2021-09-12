package controllers

import scala.collection.JavaConverters._
import akka.actor.ActorSystem
import com.google.common.io.BaseEncoding
import dao.{AssemblyReqDAO, ReqSummaryDAO}
import io.circe.Json
import io.circe.syntax._
import javax.inject._
import models.{Assembly, Summary}
import org.ergoplatform.{ErgoAddressEncoder, ErgoBox}
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, ErgoValue, NetworkType, RestApiErgoClient}
import play.api.Logger
import play.api.libs.circe.Circe
import play.api.mvc._
import scalaj.http.Http
import services.NodeService
import sigmastate.serialization.ErgoTreeSerializer
import special.collection.Coll
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

  def encodedBox(bytes: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val box = ErgoBox.sigmaSerializer.fromBytes(BaseEncoding.base16().decode(bytes))
      Ok(
        s"""{
           |  "encodedBox": "${ErgoValue.of(box).toHex}"
           |}""".stripMargin).as("application/json")
    } catch {
      case e: Exception =>
        errorResponse(e)
    }
  }

  def returnAddr(address: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      var ret: String = null
      val conf = RestApiErgoClient.create(Conf.activeNodeUrl + "/", NetworkType.MAINNET, "", null)
      conf.execute(ctx => {
        val addrEnc = new ErgoAddressEncoder(NetworkType.MAINNET.networkPrefix)
        val cur = Address.create(address).getErgoAddress.script
        val prover = ctx.newProverBuilder()
          .withDLogSecret(BigInt.apply(0).bigInteger)
          .build()
        cur.constants.foreach(c => {
          if (c.value.isInstanceOf[Coll[Byte]]) {
            try {
              val tr = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(c.value.asInstanceOf[Coll[Byte]].toArray)
              val tt = ctx.newTxBuilder()
              prover.sign(tt.boxesToSpend(Seq(tt.outBoxBuilder()
                .contract(new ErgoTreeContract(cur))
                .value(1e8.toLong)
                .build()
                .convertToInputWith("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 0)).asJava)
                .fee(1e6.toLong)
                .outputs(tt.outBoxBuilder()
                  .contract(new ErgoTreeContract(tr))
                  .value(1e8.toLong - 1e6.toLong)
                  .build())
                .sendChangeTo(Address.create("4MQyML64GnzMxZgm").getErgoAddress)
                .build()).toJson(false)
              ret = addrEnc.fromProposition(tr).get.toString

            } catch {
              case e: Exception => println(e.getMessage)
            }
          }
        })
      })
      Ok(
        s"""{
           |  "returnAddr": "$ret"
           |}""".stripMargin).as("application/json")
    } catch {
      case e: Exception =>
        errorResponse(e)
    }
  }
}
