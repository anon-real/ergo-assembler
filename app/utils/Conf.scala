package utils

import com.typesafe.config.ConfigFactory
import play.api.{Configuration, Logger}

object Conf {
  private val logger: Logger = Logger(this.getClass)
  val config: Configuration = Configuration(ConfigFactory.load())

  lazy val nodeUrl: String = readKey("node.url").replaceAll("/$", "")
  lazy val nodeApi: String = readKey("node.api_key", "")
  lazy val explorerUrl: String = readKey("explorer.url").replaceAll("/$", "")

  def readKey(key: String, default: String = null): String = {
    try {
      if(config.has(key)) config.getOptional[String](key).getOrElse(default)
      else throw config.reportError(key,s"${key} not found!")
    } catch {
        case ex: Throwable =>
          logger.error(ex.getMessage)
          null
      }
  }
}
