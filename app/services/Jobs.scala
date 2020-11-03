package services

import akka.actor.{Actor, ActorLogging}
import play.api.Logger

object JobsUtil {
  val summary = "summary"
}

class Jobs(summaryHandler: SummaryHandler) extends Actor with ActorLogging {
  private val logger: Logger = Logger(this.getClass)

  def receive = {
    case JobsUtil.summary =>
      summaryHandler.handleSummaries()
  }
}

