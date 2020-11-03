package services

import io.circe.Json
import javax.inject.{Inject, Singleton}
import play.api.Logger
import scalaj.http.Http
import io.circe.parser._
import utils.Conf

import scala.collection.mutable

@Singleton
class NodeService @Inject()() {
  private val logger: Logger = Logger(this.getClass)
  private val defaultHeader: Seq[(String, String)] = Seq[(String, String)](("Content-Type", "application/json"), ("api_key", Conf.nodeApi))

  /**
   * gets box as raw, whether box is confirmed or unconfirmed
   *
   * @param boxId box id
   * @return string representing the raw box
   */
  def getRaw(boxId: String): String = {
    val res = Http(s"${Conf.nodeUrl}/utxo/byIdBinary/$boxId").headers(defaultHeader).asString
    parse(res.body).getOrElse(Json.Null).hcursor.downField("bytes").as[String].getOrElse("")
  }

  /** *
   * get raw format of an address
   *
   * @param address ergo address
   * @return raw
   */
  def addressToRaw(address: String): String = {
    val res = Http(s"${Conf.nodeUrl}/utils/addressToRaw/$address").headers(defaultHeader).asString
    val bodyJs = parse(res.body).getOrElse(Json.Null)
    bodyJs.hcursor.downField("raw").as[String]
      .getOrElse(throw new Exception(bodyJs.hcursor.downField("detail").as[String].getOrElse("")))
  }

  /**
   * registers a scan in node to follow unspent boxes
   *
   * @param address ergo address
   * @return scan id
   */
  def registerScan(address: String): Int = {
    val body =
      s"""{
         |  "scanName": "assembler",
         |  "trackingRule": {
         |    "predicate": "equals",
         |    "value": "${addressToRaw(address)}"
         |  }
         |}""".stripMargin
    val res = Http(s"${Conf.nodeUrl}/scan/register").postData(body).headers(defaultHeader).asString
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
    val res = Http(s"${Conf.nodeUrl}/scan/deregister").postData(body).headers(defaultHeader).asString
    res.isSuccess
  }

  /**
   * broadcasts tx
   *
   * @param tx transaction
   * @return whether it was successful or not
   */
  def broadcastTx(tx: String): Boolean = {
    val res = Http(s"${Conf.nodeUrl}/transactions").postData(tx).headers(defaultHeader).asString
    res.isSuccess
  }

  /**
   * gets list of unspent boxes for a particular scanId, confirmed + unconfirmed
   *
   * @param scanId scan id
   * @return list of boxes as json
   */
  def unspentBoxesFor(scanId: Int): List[Json] = {
    val res = Http(s"${Conf.nodeUrl}/scan/unspentBoxes/$scanId").headers(defaultHeader).asString
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
    val res = Http(s"${Conf.nodeUrl}/wallet/transaction/generateUnsigned").postData(request).headers(defaultHeader).asString
    parse(res.body).getOrElse(Json.Null)
  }

  /**
   * checks whether a specific box is spent or not
   *
   * @param boxId box id
   * @return boolean in case of successfully contacting node, exception otherwise
   */
  def isSpent(boxId: String): Boolean = {
    val res = Http(s"${Conf.nodeUrl}/utxo/byId/$boxId").headers(defaultHeader).asString
    if (res.isSuccess) false
    else if (res.code == 404) true
    else {
      val body = parse(res.body).getOrElse(Json.Null)
      throw new Exception(body.noSpaces)
    }
  }

  /**
   * checks whether a specific tx is valid or not
   *
   * @param tx transaction body
   * @return boolean in case of successfully contacting node, exception otherwise
   */
  def isTxValid(tx: String): Boolean = {
    val res = Http(s"${Conf.nodeUrl}/transactions/check").postData(tx).headers(defaultHeader).asString
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
    boxes.foreach(box => box.hcursor.downField("box").as[Json].getOrElse(Json.Null).
      hcursor.downField("assets").as[Seq[Json]].getOrElse(Seq()).foreach(token => {
      val tokenId = token.hcursor.downField("tokenId").as[String].getOrElse("")
      changeTokens(tokenId) = changeTokens.getOrElse(tokenId, 0L) + token.hcursor.downField("amount").as[Long].getOrElse(0L)
    }))
    val erg = boxes.map(box => box.hcursor.downField("box").as[Json].getOrElse(Json.Null)
      .hcursor.downField("value").as[Long].getOrElse(0L)).sum

    val ids = boxes.map(box => box.hcursor.downField("box").as[Json].getOrElse(Json.Null).
      hcursor.downField("boxId").as[String].getOrElse(""))

    val changeAsset = changeTokens.map(token =>
      s"""{
         |  "tokenId": "${token._1}",
         |  "amount": ${token._2}
         |}""".stripMargin).mkString(",")
    val change =
      s"""{
         |  "address": "$address",
         |  "value": $erg,
         |  "assets": [${changeAsset.mkString(",")}]
         |}""".stripMargin

    val request =
      s"""{
         |  "requests": [$change],
         |  "fee": ${Conf.returnTxFee},
         |  "inputsRaw": [${ids.map(id => s""""${getRaw(id)}"""").mkString(",")}]
         |}""".stripMargin

    generateTx(request)
  }
}
