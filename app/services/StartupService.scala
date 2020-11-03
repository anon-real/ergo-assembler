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
                               system: ActorSystem, summaryHandler: SummaryHandler)
                              (implicit ec: ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {

  private val logger: Logger = Logger(this.getClass)

  logger.info("App started!")

  val jobs: ActorRef = system.actorOf(Props(new Jobs(summaryHandler)), "scheduler")
  system.scheduler.scheduleAtFixedRate(
    initialDelay = 10.seconds,
    interval = Conf.removeSummaryInterval.seconds,
    receiver = jobs,
    message = JobsUtil.summary
  )

  appLifecycle.addStopHook { () =>
    logger.info("App stopped!")
    Future.successful(())
  }
}
