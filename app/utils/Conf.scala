package utils

import com.typesafe.config.ConfigFactory
import play.api.{Configuration, Logger}

object Conf {
  private val logger: Logger = Logger(this.getClass)
  val config: Configuration = Configuration(ConfigFactory.load())

  lazy val publicNodes: Seq[String] = config.getOptional[Seq[String]]("node.public").getOrElse(Seq()).map(_.replaceAll("/$", ""))
  lazy val explorerUrl: String = readKey("explorer.url").replaceAll("/$", "")

  var availableNodeUrls: Seq[String] = Seq()
  var availableNodeApis: Seq[String] = Seq()
  var availableNodeWallets: Seq[String] = Seq()
  (1 until 10).foreach(nodeNum => {
    if (config.has(s"node${nodeNum}")) {
      availableNodeUrls = availableNodeUrls :+ readKey(s"node${nodeNum}.url").replaceAll("/$", "")
      availableNodeApis = availableNodeApis :+ readKey(s"node${nodeNum}.api_key", "")
      availableNodeWallets = availableNodeWallets :+ readKey(s"node${nodeNum}.wallet_pass", "")
    }
  })

  var activeNodeUrl: String = readKey("node1.url").replaceAll("/$", "")
  var activeNodeApi: String = readKey("node1.api_key", "")
  var activeNodeWallet: String = readKey("node1.wallet_pass", "")

  lazy val followRequestFor: Long = readKey("followRequestFor").toInt
  lazy val followRequestInterval: Long = readKey("followRequestInterval").toInt

  lazy val followTxFor: Long = readKey("followTxFor").toInt
  lazy val followTxForConf: Long = readKey("followTxForConf").toInt
  lazy val followTxInterval: Long = readKey("followTxInterval").toInt

  lazy val removeSummaryInterval: Long = readKey("removeSummaryInterval").toInt
  lazy val keepSummaryFor: Long = readKey("keepSummaryFor").toInt

  lazy val returnTxFee: Long = readKey("returnTxFee").toInt

  var functioning: Boolean = true
  var functioningAdmin: Boolean = true
  var ignoreTime: Boolean = false
  lazy val handleParamsInterval: Long = readKey("handleParamsInterval", "60").toInt

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
