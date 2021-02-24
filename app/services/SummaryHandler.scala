package services

import java.util.Calendar

import dao.ReqSummaryDAO
import javax.inject.Inject
import play.api.Logger
import utils.Conf
import utils.Utils._

import scala.concurrent.ExecutionContext

class SummaryHandler @Inject()(nodeService: NodeService, reqSummaryDAO: ReqSummaryDAO, ec: ExecutionContext) {
  private val logger: Logger = Logger(this.getClass)
  implicit val executionContext: ExecutionContext = ec

  def handleSummaries(): Unit = {
    logger.info("Handling out of date summaries...")
    try {
      if (Conf.ignoreTime) {
        logger.info("did not remove any summaries; ignoreTime is true")
        return
      }
      val lastValidTime = Calendar.getInstance().getTimeInMillis - Conf.keepSummaryFor * 1000
      reqSummaryDAO.beforeTime(lastValidTime) map (res => {
        res.foreach(summary => {
          reqSummaryDAO.deleteById(summary.id)
          nodeService.deregisterScan(summary.scanId)
          try {
            val txId = nodeService.returnFunds(summary.returnTo, summary.address)
            logger.info(s"returned funds for summary ${summary.scanId} with tx: ${txId}")
          } catch {
            case _: Any =>
          }
        })
        logger.info(s"removed ${res.length} summaries from db!")
      }) recover {
        case e: Throwable => logger.error(getStackTraceStr(e))
      }

    } catch {
      case e: Throwable => e.printStackTrace()
        logger.error(e.getMessage)
    }
  }

}
