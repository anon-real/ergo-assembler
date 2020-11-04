package models

import java.util.{Calendar, UUID}

import io.circe._
import io.circe.parser._


case class AssemblyReq(id: String, var scanId: Int, address: String, returnTo: String, startWhen: String, txSpec: String, timestamp: Long) {
  def elapsedInSec: Long = (Calendar.getInstance().getTimeInMillis - timestamp) / 1000

  def toRes(tx: String): AssembleRes = Assembled(this, tx)
}

object Assembly {
  def apply(reqJs: Json): AssemblyReq = {
    val address = reqJs.hcursor.downField("address").as[String].getOrElse(throw new Exception("address field is required"))
    val returnTo = reqJs.hcursor.downField("returnTo").as[String].getOrElse(throw new Exception("returnTo field is required"))
    val startWhen = reqJs.hcursor.downField("startWhen").as[Json].getOrElse(throw new Exception("startWhen field is required")).noSpaces
    val txSpec = reqJs.hcursor.downField("txSpec").as[Json].getOrElse(throw new Exception("txSpec field is required")).noSpaces
    val timestamp = Calendar.getInstance().getTimeInMillis
    val id = UUID.randomUUID().toString
    AssemblyReq(id, 0, address, returnTo, startWhen, txSpec, timestamp)
  }
}

case class AssembleRes(id: String, scanId: Int, address: String, returnTo: String, startWhen: String, txSpec: String, tx: String, timestamp: Long) {
  def elapsedInSec: Long = (Calendar.getInstance().getTimeInMillis - timestamp) / 1000

  def toReq: AssemblyReq = AssemblyReq(id, scanId, address, returnTo, startWhen, txSpec, Calendar.getInstance().getTimeInMillis)
}

object Assembled {
  def apply(id: String, scanId: Int, address: String, returnTo: String, startWhen: String, txSpec: String, tx: String): AssembleRes = {
    AssembleRes(id, scanId, address, returnTo, startWhen, txSpec, tx, Calendar.getInstance().getTimeInMillis)
  }

  def apply(req: AssemblyReq, tx: String): AssembleRes = {
    AssembleRes(req.id, req.scanId, req.address, req.returnTo, req.startWhen, req.txSpec, tx, Calendar.getInstance().getTimeInMillis)
  }
}

object Stats {
  val pending = "pending"
  val returnSuccess = "returning"
  val returnFailed = "return failed"
  val timeout = "timeout"
  val success = "success"
}

case class ReqSummary(id: String, scanId: Int, returnTo: String, tx: Option[String], timestamp: Long, details: String) {
  def elapsedInSec: Long = (Calendar.getInstance().getTimeInMillis - timestamp) / 1000

  def isReturn: Boolean = details != "success"
}

object Summary {
  def apply(id: String, scanId: Int, returnTo: String, tx: Option[String], timestamp: Long, details: String): ReqSummary =
    ReqSummary(id, scanId, returnTo, tx, timestamp, details)

  def apply(req: AssemblyReq, tx: Option[String], timestamp: Long, details: String): ReqSummary =
    Summary(req.id, req.scanId, req.returnTo, tx, timestamp, details)

  def apply(req: AssemblyReq): ReqSummary = Summary(req, Option.empty, Calendar.getInstance().getTimeInMillis, Stats.pending)

}

