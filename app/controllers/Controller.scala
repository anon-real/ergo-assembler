package controllers

import akka.actor.ActorSystem
import javax.inject._
import play.api.Logger
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Controller @Inject()(cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends AbstractController(cc) {

  private val logger: Logger = Logger(this.getClass)

  /**
   * Home! shows list of teams which user participate in!
   */
  def home = Action { implicit request =>
    Ok("hiiiiiiii")
  }
}
