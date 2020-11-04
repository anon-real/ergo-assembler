package services

import akka.actor.{Actor, ActorLogging}
import play.api.Logger

object JobsUtil {
  val summary = "summary"
  val request = "request"
}

class Jobs(summaryHandler: SummaryHandler, requestHandler: RequestHandler) extends Actor with ActorLogging {
  private val logger: Logger = Logger(this.getClass)

  def receive = {
    case JobsUtil.summary =>
      summaryHandler.handleSummaries()

    case JobsUtil.request =>
      requestHandler.handleReqs()
  }
}

