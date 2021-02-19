package utils

import com.typesafe.config.ConfigFactory
import play.api.{Configuration, Logger}

object Conf {
  private val logger: Logger = Logger(this.getClass)
  val config: Configuration = Configuration(ConfigFactory.load())

  lazy val nodeUrl: String = readKey("node.url").replaceAll("/$", "")
  lazy val nodeApi: String = readKey("node.api_key", "")
  lazy val walletPass: String = readKey("node.wallet_pass", "")
  lazy val explorerUrl: String = readKey("explorer.url").replaceAll("/$", "")

  lazy val followRequestFor: Long = readKey("followRequestFor").toInt
  lazy val followRequestInterval: Long = readKey("followRequestInterval").toInt

  lazy val followTxFor: Long = readKey("followTxFor").toInt
  lazy val followTxForConf: Long = readKey("followTxForConf").toInt
  lazy val followTxInterval: Long = readKey("followTxInterval").toInt

  lazy val removeSummaryInterval: Long = readKey("removeSummaryInterval").toInt
  lazy val keepSummaryFor: Long = readKey("keepSummaryFor").toInt

  lazy val returnTxFee: Long = readKey("returnTxFee").toInt

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
