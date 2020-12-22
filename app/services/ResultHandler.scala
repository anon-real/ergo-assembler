package services

import java.util.Calendar

import dao.{AssembleResDAO, AssemblyReqDAO, ReqSummaryDAO}
import io.circe.Json
import io.circe.jawn._
import javax.inject.Inject
import models.AssembleRes
import play.api.Logger
import utils.Conf
import utils.Utils._

import scala.concurrent.ExecutionContext

class ResultHandler @Inject()(nodeService: NodeService, assemblyReqDAO: AssemblyReqDAO, ec: ExecutionContext,
                              assembleResDAO: AssembleResDAO, reqSummaryDAO: ReqSummaryDAO) {
  private val logger: Logger = Logger(this.getClass)
  implicit val executionContext: ExecutionContext = ec

  def handleResults(): Unit = {
    logger.info("Handling results...")
    val lastValidTime = Calendar.getInstance().getTimeInMillis - Conf.followTxFor * 1000
    assembleResDAO.all.map(reqs => {
      reqs.foreach(req => {
        try {
          if (req.timestamp <= lastValidTime) {
            println(s"timeout for ${req.id} - ${req.scanId}; will stop following the tx!")
            assembleResDAO.deleteById(req.id) recover {
              case e: Throwable => logger.error(getStackTraceStr(e))
            }

          } else {
            checkRes(req)
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

  def checkRes(res: AssembleRes): Unit = {
    if (!nodeService.isTxValid(res.tx)) {
      // it is either mined or really invalid!
      val tx = parse(res.tx).getOrElse(Json.Null)
      val out = tx.hcursor.downField("outputs").as[Seq[Json]].getOrElse(Seq()).head
        .hcursor.downField("boxId").as[String].getOrElse("")
      if (!nodeService.isSpent(out)) {
        logger.info(s"tx is mined for ${res.id} - ${res.scanId}; will stop following it!")
        assembleResDAO.deleteById(res.id) recover {
          case e: Throwable => logger.error(getStackTraceStr(e))
        }
      } else {
        assembleResDAO.deleteById(res.id) map (_ => {
          logger.info(s"tx is invalid for ${res.id} - ${res.scanId}; will try to create tx again or return it!")
          assemblyReqDAO.insert(res.toReq) recover {
            case e: Throwable => logger.error(getStackTraceStr(e))
          }

        }) recover {
          case e: Throwable => logger.error(getStackTraceStr(e))
        }
      }
      nodeService.broadcastTx(res.tx)
    }
  }
}
