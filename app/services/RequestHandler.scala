package services

import java.util.Calendar

import dao.{AssembleResDAO, AssemblyReqDAO, ReqSummaryDAO}
import io.circe.jawn._
import io.circe.{Json, JsonNumber}
import javax.inject.Inject
import models.{Assembled, AssemblyReq, Stats}
import play.api.Logger
import utils.Conf
import utils.Utils._

import scala.collection.mutable
import scala.concurrent.ExecutionContext

class RequestHandler @Inject()(nodeService: NodeService, assemblyReqDAO: AssemblyReqDAO, ec: ExecutionContext,
                               assembleResDAO: AssembleResDAO, reqSummaryDAO: ReqSummaryDAO) {
  private val logger: Logger = Logger(this.getClass)
  implicit val executionContext: ExecutionContext = ec

  def handleReqs(): Unit = {
    logger.info("Handling requests...")
    if (!nodeService.isWalletUnlocked) {
      logger.info("Wallet is locked, going to unlock it...")
      logger.error(nodeService.unlockWallet(Conf.activeNodeWallet).toString)
    }
    val lastValidTime = Calendar.getInstance().getTimeInMillis - Conf.followRequestFor * 1000
    assemblyReqDAO.all.map(reqs => {
      reqs.foreach(req => {
        try {
          if (req.timestamp <= lastValidTime && !Conf.ignoreTime) {
            handleRemoval(req, Stats.timeout)

          } else {
            handleReq(req)
          }

        } catch {
          case e: Throwable => e.printStackTrace()
            logger.error(e.getMessage)
        }
      })
    }) recover {
      case e: Throwable => logger.error(getStackTraceStr(e))
    }
  }

  def handleRemoval(req: AssemblyReq, stat: String): Unit = {
    logger.info(s"will remove request: ${req.id} with scanId: ${req.scanId}")
    val boxes = nodeService.unspentBoxesFor(req.scanId)
    if (boxes.nonEmpty) {
      val tx = nodeService.sendBoxesTo(boxes.map(box => box.hcursor.downField("box").as[Json].getOrElse(Json.Null)), req.returnTo)
      val ok = tx.hcursor.keys.getOrElse(Seq()).exists(key => key == "id")
      if (ok) {
        assembleResDAO.insert(Assembled(req, tx.noSpaces)) recover {
          case e: Throwable => logger.error(getStackTraceStr(e))
        }
        reqSummaryDAO.partialUpdate(req.id, tx.noSpaces, Stats.returnSuccess) recover {
          case e: Throwable => logger.error(getStackTraceStr(e))
        }
        logger.info(s"return tx for ${req.id} - ${req.scanId} successfully: ${tx.hcursor.downField("id").as[String].getOrElse("")}")
        nodeService.broadcastTx(tx.noSpaces)

      } else {
        logger.warn(s"could not return assets of ${req.id} - ${req.scanId}, error: ${tx.noSpaces}")
        reqSummaryDAO.partialUpdate(req.id, null, Stats.returnFailed) map (_ => {
          nodeService.deregisterScan(req.scanId)
        }) recover {
          case e: Throwable => logger.error(getStackTraceStr(e))
        }
      }
    } else {
      reqSummaryDAO.partialUpdate(req.id, null, Stats.timeout) map (_ => {
        nodeService.deregisterScan(req.scanId)
      }) recover {
        case e: Throwable => logger.error(getStackTraceStr(e))
      }
    }

    assemblyReqDAO.deleteById(req.id)
  }

  def handleReq(req: AssemblyReq): Unit = {
    val boxes = nodeService.unspentBoxesFor(req.scanId)

    val changeTokens: mutable.Map[String, Long] = mutable.Map.empty
    boxes.foreach(box => box.hcursor.downField("box").as[Json].getOrElse(Json.Null).
      hcursor.downField("assets").as[Seq[Json]].getOrElse(Seq()).foreach(token => {
      val tokenId = token.hcursor.downField("tokenId").as[String].getOrElse("")
      changeTokens(tokenId) = changeTokens.getOrElse(tokenId, 0L) + token.hcursor.downField("amount").as[Long].getOrElse(0L)
    }))
    changeTokens("erg") = boxes.map(box => box.hcursor.downField("box").as[Json].getOrElse(Json.Null)
      .hcursor.downField("value").as[Long].getOrElse(0L)).sum
    val when = parse(req.startWhen).getOrElse(parse("{}").getOrElse(Json.Null))
    val whenKeys = when.hcursor.keys.getOrElse(Seq())
    val ok = whenKeys.forall(key => {
      when.hcursor.downField(key).as[Long].getOrElse(0L) == changeTokens.getOrElse(key, 0L)
    })
    if (ok) {
      logger.info(s"all ok for ${req.id} - ${req.scanId}, starting...")
      startTx(req, boxes)

    } else {
      var more = when.hcursor.keys.getOrElse(Seq()).exists(key => {
        when.hcursor.downField(key).as[Long].getOrElse(0L) < changeTokens.getOrElse(key, 0L)
      })
      if (!(req.txSpec contains "$userIns.token")) {
        more = more || !changeTokens.forall(tok => whenKeys.toList.contains(tok._1))
      }

      if (more) {
        logger.info(s"more deposit than requested ${req.id} - ${req.scanId}, removing...")
        handleRemoval(req, Stats.returnFailed)
      }
    }
  }

  def startTx(req: AssemblyReq, boxes: Seq[Json]): Unit = {
    val txSpec = parse(req.txSpec).getOrElse(Json.Null)
    val fee = txSpec.hcursor.downField("fee").as[Long].getOrElse(Conf.returnTxFee)
    var txReqs = txSpec.hcursor.downField("requests").as[Seq[Json]].getOrElse(Seq())
    boxes.foreach(box => {
      val assets = box.hcursor.downField("box").as[Json].getOrElse(Json.Null)
        .hcursor.downField("assets").as[Seq[Json]].getOrElse(Seq())
      if (assets.length == 1) {
        val tokenId = assets.head.hcursor.downField("tokenId").as[String].getOrElse("")
        val amount = assets.head.hcursor.downField("amount").as[JsonNumber].getOrElse(null)
        txReqs = txReqs.map(req => {
          if (req.hcursor.downField("assets").as[Seq[Json]].getOrElse(Seq()).isEmpty) req
          else {
            req.hcursor.downField("assets").withFocus(assets => {
              assets.mapArray(lstAssets => {
                lstAssets.map(asset => {
                  if (asset.hcursor.downField("tokenId").as[String].getOrElse("") == "$userIns.token") {
                    asset.hcursor.downField("tokenId").withFocus(_.mapString(_ => tokenId)).top.get
                      .hcursor.downField("amount").withFocus(_.mapNumber(_ => amount)).top.get
                  } else asset
                })
              })
            }).top.get
          }
        })
      }
    })
    var inputRaws: Seq[String] = Seq()
    val userRaws = boxes.map(box => box.hcursor.downField("box").as[Json].getOrElse(Json.Null).
      hcursor.downField("boxId").as[String].getOrElse("")).map(id => nodeService.getRaw(id))
    txSpec.hcursor.downField("inputs").as[Seq[String]].getOrElse(Seq()).foreach(id => {
      if (id == "$userIns") inputRaws = inputRaws ++ userRaws
      else inputRaws = inputRaws :+ nodeService.getRaw(id)
    })
    val dataInputRaws: Seq[String] = txSpec.hcursor.downField("dataInputs").as[Seq[String]].getOrElse(Seq())
      .map(id => nodeService.getRaw(id))
    val body =
      s"""{
         |  "requests": [${txReqs.map(_.noSpaces).mkString(",")}],
         |  "inputsRaw": [${inputRaws.map(id => s""""$id"""").mkString(",")}],
         |  "dataInputsRaw": [${dataInputRaws.map(id => s""""$id"""").mkString(",")}],
         |  "fee": $fee
         |}""".stripMargin
    val tx = nodeService.generateTx(body)
    val ok = tx.hcursor.keys.getOrElse(Seq()).exists(key => key == "id")
    if (ok) {
      logger.info(s"generated tx for ${req.id} - ${req.scanId} successfully: ${tx.hcursor.downField("id").as[String].getOrElse("")}")
      assemblyReqDAO.deleteById(req.id) map (_ => {
        assembleResDAO.insert(Assembled(req, tx.noSpaces)) recover {
          case e: Throwable => logger.error(getStackTraceStr(e))
        }
        reqSummaryDAO.partialUpdate(req.id, tx.noSpaces, Stats.success) recover {
          case e: Throwable => logger.error(getStackTraceStr(e))
        }
        nodeService.broadcastTx(tx.noSpaces)
      })

    } else {
      logger.warn(s"could not generate tx, returning.... ${req.id} - ${req.scanId}, error: ${tx.noSpaces}")
      handleRemoval(req, Stats.returnFailed)
    }
  }
}
