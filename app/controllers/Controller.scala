package controllers

import scala.collection.JavaConverters._
import akka.actor.ActorSystem
import com.google.common.io.BaseEncoding
import dao.{AssemblyReqDAO, ReqSummaryDAO}
import io.circe.Json
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
import sigmastate.Values.ErgoTree
import sigmastate.serialization.ErgoTreeSerializer
import special.collection.Coll
import special.sigma.SigmaProp
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
      Ok(
        s"""{
           |  "txId": "${nodeService.returnFunds(mine, address)}"
           |}""".stripMargin).as("application/json")
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

  def getScanAddress(address: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      val addr = nodeService.getScanAddress(address)
      Ok(
        s"""{
           |  "scanAddress": "${addr}"
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
          var tr: ErgoTree = null
          try {
            if (c.value.isInstanceOf[Coll[Byte]]) {
              tr = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(c.value.asInstanceOf[Coll[Byte]].toArray)
            } else if (c.opType.toString().contains("SigmaProp")) {
              tr = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(c.value.asInstanceOf[SigmaProp].propBytes.toArray)
            }
          }
          catch {
            case e: OutOfMemoryError => println(s"OutOfMemoryError ${e.getMessage}")
            case e: Exception => println(e.getMessage)
          }
          try {
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

  def walletAddress: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      if (!Conf.functioning || !Conf.functioningAdmin) throw new Exception("Assembler is not functioning currently")
      Ok(
        s"""{
           |  "address": "${nodeService.getWalletAddress}"
           |}""".stripMargin).as("application/json")
    } catch {
      case e: Exception => errorResponse(e)
    }
  }

  def getBankBox: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      if (!Conf.functioning || !Conf.functioningAdmin) throw new Exception("Assembler is not functioning currently")
      // val bank = nodeService.unspentBoxesFor(Conf.bankScanId, 0).head.hcursor.downField("box").as[Json].getOrElse(Json.Null)
      val bank = nodeService.chainedBank()
      Ok(bank).as("application/json")
    } catch {
      case e: Exception => errorResponse(e)
    }
  }

  def getPreHeaders: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      if (!Conf.functioning || !Conf.functioningAdmin) throw new Exception("Assembler is not functioning currently")
      val headers = nodeService.getPreHeaders
      Ok(headers).as("application/json")
    } catch {
      case e: Exception => errorResponse(e)
    }
  }

  def getOracleBox: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      if (!Conf.functioning || !Conf.functioningAdmin) throw new Exception("Assembler is not functioning currently")
      val oracle = nodeService.unspentBoxesFor(Conf.oracleScanId, 0).head.hcursor.downField("box").as[Json].getOrElse(Json.Null)
      Ok(oracle).as("application/json")
    } catch {
      case e: Exception => errorResponse(e)
    }
  }

  def getHeight: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      if (!Conf.functioning || !Conf.functioningAdmin) throw new Exception("Assembler is not functioning currently")
      val height = nodeService.getHeight(Conf.activeNodeUrl)
      Ok(
        s"""{
           |  "height": $height
           |}""".stripMargin).as("application/json")
    } catch {
      case e: Exception => errorResponse(e)
    }
  }

  def broadcast: Action[Json] = Action(circe.json) { implicit request =>
    try {
      if (!Conf.functioning || !Conf.functioningAdmin) throw new Exception("Assembler is not functioning currently")
      val tx = request.body.noSpaces
      println(tx)
      Ok(
        s"""{
           |  "txId": ${nodeService.broadcastRes(tx)}
           |}""".stripMargin).as("application/json")
    } catch {
      case e: Exception => errorResponse(e)
    }
  }

  def getFunds(address: String, assetId: String, amount: Long): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    try {
      if (!Conf.functioning || !Conf.functioningAdmin) throw new Exception("Assembler is not functioning currently")
      val offset = 0
      val limit = 100
      var needed = amount
      var chosen = Seq.empty[Json]

      val tree = nodeService.addressToTree(address)
      val unconfirmedTxs = nodeService.getUnconfirmedTxFor(tree)
      val unconfirmedInputs = unconfirmedTxs.flatMap(tx => tx.hcursor.downField("inputs").as[IndexedSeq[Json]].getOrElse(IndexedSeq.empty[Json]))
      val unconfirmedOutputs = unconfirmedTxs.flatMap(tx => tx.hcursor.downField("outputs").as[IndexedSeq[Json]].getOrElse(IndexedSeq.empty[Json]))
        .filter(output => output.hcursor.downField("ergoTree").as[String].getOrElse("") == tree)

      var boxes = nodeService.unspentAssetsFor(address, offset, limit)

      unconfirmedOutputs.foreach(output => {
        val boxId = output.hcursor.downField("boxId").as[String].getOrElse("")
        val ind = boxes.indexWhere(box => box.hcursor.downField("boxId").as[String].getOrElse("") == boxId)
        if (ind == -1) boxes = boxes :+ output
      })


      if (assetId == "ERG") boxes = boxes.sortBy(box => {
        val boxId = box.hcursor.downField("boxId").as[String].getOrElse("")
        val ind2 = unconfirmedInputs.indexWhere(input => input.hcursor.downField("boxId").as[String].getOrElse("") == boxId)
        val ind3 = unconfirmedOutputs.indexWhere(output => output.hcursor.downField("boxId").as[String].getOrElse("") == boxId)

        if (ind2 != -1) -2L
        else if (ind3 != -1) -1L
        box.hcursor.downField("value").as[Long].getOrElse(0L)
      }).reverse
      else boxes = boxes.sortBy(box => {
        val assets = box.hcursor.downField("assets").as[IndexedSeq[Json]].getOrElse(IndexedSeq.empty[Json])
        val ind = assets.indexWhere(asset => asset.hcursor.downField("tokenId").as[String].getOrElse("") == assetId)

        // if box in unconfirmed inputs, then return -1
        val boxId = box.hcursor.downField("boxId").as[String].getOrElse("")
        val ind2 = unconfirmedInputs.indexWhere(input => input.hcursor.downField("boxId").as[String].getOrElse("") == boxId)
        val ind3 = unconfirmedOutputs.indexWhere(output => output.hcursor.downField("boxId").as[String].getOrElse("") == boxId)


        if (ind2 != -1) -2L
        else if (ind3 != -1) -1L
        else if (ind == -1) 0L
        else {
          val asset = assets(ind)
          asset.hcursor.downField("amount").as[Long].getOrElse(0L)
        }
      }).reverse


      boxes.foreach(box => {
        if (needed > 0) {
          chosen = chosen :+ box

          if (assetId == "ERG") needed -= box.hcursor.downField("value").as[Long].getOrElse(0L)
          else {
            val assets = box.hcursor.downField("assets").as[IndexedSeq[Json]].getOrElse(IndexedSeq.empty[Json])
            val ind = assets.indexWhere(asset => asset.hcursor.downField("tokenId").as[String].getOrElse("") == assetId)
            if (ind == -1) needed -= 0L
            else {
              val asset = assets(ind)
              needed -= asset.hcursor.downField("amount").as[Long].getOrElse(0L)
            }
          }
        }
      })
      // return chosen
      val boxesJson: String = chosen.map { case box =>
        box.toString()
      }.mkString("[", ",", "]")
      Ok(
        s"""${boxesJson}""".stripMargin).as("application/json")
    } catch {
      case e: Exception => errorResponse(e)
    }
  }
}
