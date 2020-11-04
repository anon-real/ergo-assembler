package services

import akka.actor.{ActorRef, ActorSystem, Props}
import javax.inject._
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.inject.ApplicationLifecycle
import slick.jdbc.JdbcProfile
import utils.Conf

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

@Singleton
class StartupService @Inject()(protected val dbConfigProvider: DatabaseConfigProvider, appLifecycle: ApplicationLifecycle,
                               system: ActorSystem, summaryHandler: SummaryHandler, requestHandler: RequestHandler, resultHandler: ResultHandler)
                              (implicit ec: ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {

  private val logger: Logger = Logger(this.getClass)

  logger.info("App started!")

  val jobs: ActorRef = system.actorOf(Props(new Jobs(summaryHandler, requestHandler, resultHandler)), "scheduler")
  system.scheduler.scheduleAtFixedRate(
    initialDelay = 5.seconds,
    interval = Conf.removeSummaryInterval.seconds,
    receiver = jobs,
    message = JobsUtil.summary
  )

  system.scheduler.scheduleAtFixedRate(
    initialDelay = 10.seconds,
    interval = Conf.followRequestInterval.seconds,
    receiver = jobs,
    message = JobsUtil.request
  )

  system.scheduler.scheduleAtFixedRate(
    initialDelay = 30.seconds,
    interval = Conf.followTxInterval.seconds,
    receiver = jobs,
    message = JobsUtil.result
  )

  appLifecycle.addStopHook { () =>
    logger.info("App stopped!")
    Future.successful(())
  }
}
