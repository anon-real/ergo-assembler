package services

import io.circe.Json
import io.circe.parser._
import javax.inject.{Inject, Singleton}
import play.api.Logger
import scalaj.http.Http
import scorex.util.encode.Base16
import sigmastate.Values.ByteArrayConstant
import sigmastate.serialization.ValueSerializer
import utils.Conf

import scala.collection.mutable

@Singleton
class NodeService @Inject()() {
  private val logger: Logger = Logger(this.getClass)
  var defaultHeader: Seq[(String, String)] = Seq[(String, String)](("Content-Type", "application/json"), ("api_key", Conf.activeNodeApi))

  /**
   * gets box as raw, whether box is confirmed or unconfirmed
   *
   * @param boxId box id
   * @return string representing the raw box
   */
  def getRaw(boxId: String): String = {
    val unconfirmed = Http(s"${Conf.activeNodeUrl}/utxo/withPool/byIdBinary/$boxId").headers(defaultHeader).asString
    val unc = parse(unconfirmed.body).getOrElse(Json.Null).hcursor.downField("bytes").as[String].getOrElse("")
    if (unc.isEmpty) {
      val res = Http(s"${Conf.activeNodeUrl}/utxo/byIdBinary/$boxId").headers(defaultHeader).asString
      val raw = parse(res.body).getOrElse(Json.Null).hcursor.downField("bytes").as[String].getOrElse("")
      if (raw.isEmpty) logger.error(s"UTXO not exists $boxId according to node")
      raw
    } else unc
  }

  /** *
   * get raw format of an address
   *
   * @param address ergo address
   * @return raw
   */
  def addressToRaw(address: String): String = {
    val res = Http(s"${Conf.activeNodeUrl}/utils/addressToRaw/$address").headers(defaultHeader).asString
    val bodyJs = parse(res.body).getOrElse(Json.Null)
    bodyJs.hcursor.downField("raw").as[String]
      .getOrElse(throw new Exception(bodyJs.hcursor.downField("detail").as[String].getOrElse("")))
  }

  def getScanAddress(address: String): String = {
    val bs = Base16.decode(addressToRaw(address)).get
    val bac = ByteArrayConstant(bs)
    Base16.encode(ValueSerializer.serialize(bac))
  }

  /**
   * registers a scan in node to follow unspent boxes
   *
   * @param address ergo address
   * @return scan id
   */
  def registerScan(address: String): Int = {
    val bs = Base16.decode(addressToRaw(address)).get
    val bac = ByteArrayConstant(bs)
    val encoded = Base16.encode(ValueSerializer.serialize(bac))
    val body =
      s"""{
         |  "scanName": "assembler",
         |  "walletInteraction": "off",
         |  "removeOffchain": false,
         |  "trackingRule": {
         |    "predicate": "equals",
         |    "value": "$encoded"
         |  }
         |}""".stripMargin
    val res = Http(s"${Conf.activeNodeUrl}/scan/register").postData(body).headers(defaultHeader).asString
    val bodyJs = parse(res.body).getOrElse(Json.Null)
    bodyJs.hcursor.downField("scanId").as[Int]
      .getOrElse(throw new Exception(s"Could not register scan ${bodyJs.hcursor.downField("detail").as[String].getOrElse("")}"))
  }

  /**
   * deregister an registered scan
   *
   * @param scanId scanId
   * @return result of deregisteration
   */
  def deregisterScan(scanId: Int): Boolean = {
    val body =
      s"""{
         |  "scanId": $scanId
         |}""".stripMargin
    val res = Http(s"${Conf.activeNodeUrl}/scan/deregister").postData(body).headers(defaultHeader).asString
    res.isSuccess
  }

  /**
   * broadcasts tx
   *
   * @param tx transaction
   * @return whether it was successful or not
   */
  def broadcastTx(tx: String): Boolean = {
    val res = Http(s"${Conf.activeNodeUrl}/transactions").postData(tx).headers(defaultHeader).asString
    res.isSuccess
  }

  /**
   * gets list of unspent boxes for a particular scanId, confirmed + unconfirmed
   *
   * @param scanId scan id
   * @return list of boxes as json
   */
  def unspentBoxesFor(scanId: Int, minConf: Int = -1): List[Json] = {
    val res = Http(s"${Conf.activeNodeUrl}/scan/unspentBoxes/$scanId?minConfirmations=${minConf}").headers(defaultHeader).asString
    val bodyJs = parse(res.body).getOrElse(Json.Null)
    bodyJs.as[List[Json]]
      .getOrElse(throw new Exception(bodyJs.hcursor.downField("detail").as[String].getOrElse("")))

  }

  /**
   * generates arbitrary tx
   *
   * @param request tx request
   * @return tx json or failure json in case of error
   */
  def generateTx(request: String): Json = {
    val res = Http(s"${Conf.activeNodeUrl}/wallet/transaction/generate").postData(request).headers(defaultHeader).asString
    parse(res.body).getOrElse(Json.Null)
  }

  /**
   * checks whether a specific box is spent or not
   *
   * @param boxId box id
   * @return boolean in case of successfully contacting node, exception otherwise
   */
  def isSpent(boxId: String): Boolean = {
    val res = Http(s"${Conf.activeNodeUrl}/utxo/byId/$boxId").headers(defaultHeader).asString
    if (res.isSuccess) false
    else if (res.code == 404) true
    else {
      val body = parse(res.body).getOrElse(Json.Null)
      throw new Exception(body.noSpaces)
    }
  }

  /**
   * gets unspent box
   *
   * @param boxId box id
   * @return box if exists
   */
  def getUnspentBox(boxId: String): Json = {
    val res = Http(s"${Conf.activeNodeUrl}/utxo/withPool/byId/$boxId").headers(defaultHeader).asString
    parse(res.body).getOrElse(throw new Exception("No such box - maybe node is not synced"))
  }

  /**
   * checks whether a specific tx is valid or not
   *
   * @param tx transaction body
   * @return boolean in case of successfully contacting node, exception otherwise
   */
  def isTxValid(tx: String): Boolean = {
    val res = Http(s"${Conf.activeNodeUrl}/transactions/check").postData(tx).headers(defaultHeader).asString
    if (res.isSuccess) true
    else if (res.code == 400) false
    else {
      val body = parse(res.body).getOrElse(Json.Null)
      throw new Exception(body.noSpaces)
    }
  }

  /**
   * sends all assets of boxes to the address
   *
   * @param boxes   list of boxes
   * @param address ergo address
   */
  def sendBoxesTo(boxes: Seq[Json], address: String): Json = {
    val changeTokens: mutable.Map[String, Long] = mutable.Map.empty
    boxes.foreach(box => box.hcursor.downField("assets").as[Seq[Json]].getOrElse(Seq()).foreach(token => {
      val tokenId = token.hcursor.downField("tokenId").as[String].getOrElse("")
      changeTokens(tokenId) = changeTokens.getOrElse(tokenId, 0L) + token.hcursor.downField("amount").as[Long].getOrElse(0L)
    }))
    val erg = boxes.map(box => box.hcursor.downField("value").as[Long].getOrElse(0L)).sum - Conf.returnTxFee

    val ids = boxes.map(box => box.hcursor.downField("boxId").as[String].getOrElse(""))

    val changeAsset = changeTokens.map(token =>
      s"""{
         |  "tokenId": "${token._1}",
         |  "amount": ${token._2}
         |}""".stripMargin).mkString(",")
    val change =
      s"""{
         |  "address": "$address",
         |  "value": $erg,
         |  "assets": [${changeAsset}]
         |}""".stripMargin

    val request =
      s"""{
         |  "requests": [$change],
         |  "fee": ${Conf.returnTxFee},
         |  "inputsRaw": [${ids.map(id => s""""${getRaw(id)}"""").mkString(",")}]
         |}""".stripMargin

    println(request)
    generateTx(request)
  }

  /**
   * compiles a script
   *
   * @param script script body
   * @return p2s address
   */
  def compile(script: String): String = {
    val body = "{\"source\":\"" + script.replaceAll("\n", "\\\\n")
      .replaceAll("\"", "\\\\\"") + "\"}"
    val res = Http(s"${Conf.activeNodeUrl}/script/p2sAddress").postData(body).headers(defaultHeader).asString
    val det = parse(res.body).getOrElse(Json.Null)
    if (res.isSuccess) det.hcursor.downField("address").as[String].getOrElse("")
    else throw new Exception(det.hcursor.downField("detail").as[String].getOrElse(""))
  }

  /**
   * get wallet status
   *
   * @return true if wallet is unlocked, lese false
   */
  def isWalletUnlocked: Boolean = {
    val res = Http(s"${Conf.activeNodeUrl}/wallet/status").headers(defaultHeader).asString
    val bodyJs = parse(res.body).getOrElse(Json.Null)
    bodyJs.hcursor.downField("isUnlocked").as[Boolean].getOrElse(false)
  }

  /**
   * unlocks wallet
   *
   * @param walletPass wallet password
   * @return status
   */
  def unlockWallet(walletPass: String): String = {
    val passJson =
      s"""{
         |  "pass": "$walletPass"
         |}""".stripMargin
    val res = Http(s"${Conf.activeNodeUrl}/wallet/unlock").postData(passJson).headers(defaultHeader).asString
    res.body
  }

  /**
   * returns funds
   *
   * @return tx id if successful
   */
  def returnFunds(mine: String, address: String): String = {
    val requestProperties = Map(
      "User-Agent" -> "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)",
      "Accept"-> "application/json"
    )
    val res = Http(s"https://api.ergoplatform.com/api/v0/transactions/boxes/byAddress/unspent/${address}")
      .headers(requestProperties).asString

    val expBoxes = io.circe.parser.parse(res.body).getOrElse(Json.Null).as[Seq[Json]].getOrElse(Seq())
    if (expBoxes.isEmpty) throw new Exception("No funds to return")

    val boxes = expBoxes.map(box => box.hcursor.downField("id").as[String].getOrElse(throw new Exception("wrong format")))
      .map(id => getUnspentBox(id))
    val tx = sendBoxesTo(boxes, mine)
    val ok = tx.hcursor.keys.getOrElse(Seq()).exists(key => key == "id")
    println(tx.noSpaces)
    if (ok) {
      broadcastTx(tx.noSpaces)
      tx.hcursor.downField("id").as[String].getOrElse("")
    }
    else throw new Exception(s"Could not generate tx, ${tx.noSpaces}")
  }

  /**
   * current node's height
   *
   * @param url node url
   * @return height
   */
  def getHeight(url: String): Int = {
    try {
      val info = Http(s"$url/info").headers(Seq[(String, String)](("Content-Type", "application/json"))).asString
      parse(info.body).getOrElse(Json.Null).hcursor.downField("fullHeight").as[Int].getOrElse(0)
    } catch {
      case _: Throwable => 0
    }
  }

  /**
   * gets one of the wallet addresses of the node
   *
   * @return wallet address
   */
  def getWalletAddress: String = {
    val res = Http(s"${Conf.activeNodeUrl}/wallet/addresses").headers(defaultHeader).asString
    val bodyJs = parse(res.body).getOrElse(Json.Null)
    bodyJs.as[Seq[String]]
      .getOrElse(Seq("none")).head
  }
}
