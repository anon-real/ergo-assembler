package services

import akka.actor.{Actor, ActorLogging}
import play.api.Logger

object JobsUtil {
  val summary = "summary"
  val request = "request"
  val result = "result"
}

class Jobs(summaryHandler: SummaryHandler, requestHandler: RequestHandler, resultHandler: ResultHandler) extends Actor with ActorLogging {
  private val logger: Logger = Logger(this.getClass)

  def receive = {
    case JobsUtil.summary =>
      summaryHandler.handleSummaries()

    case JobsUtil.request =>
      requestHandler.handleReqs()

    case JobsUtil.result =>
      resultHandler.handleResults()
  }
}

