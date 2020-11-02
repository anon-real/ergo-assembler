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
   * gets box as raw
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
}
