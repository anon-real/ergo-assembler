package services

import akka.actor.{Actor, ActorLogging}
import io.circe.Json
import io.circe.parser.parse
import play.api.Logger
import scalaj.http.Http
import utils.Conf

object JobsUtil {
  val summary = "summary"
  val request = "request"
  val result = "result"
  val handleParams = "params"
}

class Jobs(summaryHandler: SummaryHandler, requestHandler: RequestHandler, resultHandler: ResultHandler, nodeService: NodeService) extends Actor with ActorLogging {
  private val logger: Logger = Logger(this.getClass)

  def handleParams(): Unit = {
    val maxHeight: Int = Conf.publicNodes.map(url => nodeService.getHeight(url)).max
    val curHeight = nodeService.getHeight(Conf.activeNodeUrl)

    if (maxHeight - curHeight < 3) {
      if (!Conf.functioning) {
        logger.info(s"Node is synced: $curHeight - ${Conf.activeNodeUrl}. Will start functioning")
        Conf.functioning = true
      }
    } else {
      Conf.functioning = false
      logger.error(s"Current active node ${Conf.activeNodeUrl} is not OK, stopped functioning. Will try to switch...")
      Conf.availableNodeUrls.zipWithIndex.foreach(url => {
        val height = nodeService.getHeight(url._1)
        if (maxHeight - height < 3) {
          Conf.activeNodeUrl = url._1
          Conf.activeNodeApi = Conf.availableNodeApis(url._2)
          Conf.activeNodeWallet = Conf.availableNodeWallets(url._2)
          Conf.functioning = true
          logger.info(s"Found a good node to switch to ${url._1}, started functioning")
        }
      })
    }
  }

  def receive = {
    case JobsUtil.summary =>
      if (Conf.functioning && Conf.functioningAdmin)
        summaryHandler.handleSummaries()
      else logger.info(s"functioning is stopped - functioning: ${Conf.functioning}, admin; ${Conf.functioningAdmin}")

    case JobsUtil.request =>
      if (Conf.functioning && Conf.functioningAdmin)
        requestHandler.handleReqs()
      else logger.info(s"functioning is stopped - functioning: ${Conf.functioning}, admin; ${Conf.functioningAdmin}")

    case JobsUtil.result =>
      if (Conf.functioning && Conf.functioningAdmin)
        resultHandler.handleResults()
      else logger.info(s"functioning is stopped - functioning: ${Conf.functioning}, admin; ${Conf.functioningAdmin}")

    case JobsUtil.handleParams =>
      handleParams()
  }


}

