package models

import java.util.{Calendar, UUID}

import io.circe._
import io.circe.parser._


case class AssemblyReq(id: String, address: String, returnTo: String, startWhen: String, txSpec: String, requestTimestamp: Long) {
  def elapsedInSec: Long = (Calendar.getInstance().getTimeInMillis - requestTimestamp) / 1000

  def toRes(tx: String, details: String): AssembleRes = AssembleRes(this, tx, details)
}

object AssemblyReq {
  def apply(reqJs: Json): AssemblyReq = {
    val address = reqJs.hcursor.downField("address").as[String].getOrElse(throw new Exception("address field is required"))
    val returnTo = reqJs.hcursor.downField("returnTo").as[String].getOrElse(throw new Exception("returnTo field is required"))
    val startWhen = reqJs.hcursor.downField("startWhen").as[Json].getOrElse(throw new Exception("startWhen field is required")).noSpaces
    val txSpec = reqJs.hcursor.downField("txSpec").as[Json].getOrElse(throw new Exception("txSpec field is required")).noSpaces
    val timestamp = Calendar.getInstance().getTimeInMillis
    val id = UUID.randomUUID().toString
    new AssemblyReq(id, address, returnTo, startWhen, txSpec, timestamp)
  }
}

case class AssembleRes(id: String, address: String, returnTo: String, startWhen: String, txSpec: String, txId: String, tx: String, confNum: Int, assembleTimestamp: Long, details: String) {
  def elapsedInSec: Long = (Calendar.getInstance().getTimeInMillis - assembleTimestamp) / 1000

  def toReq: AssemblyReq = new AssemblyReq(id, address, returnTo, startWhen, txSpec, Calendar.getInstance().getTimeInMillis)

  def toArc: Archive = Archive(this)

  def isConfirmed: Boolean = confNum > 0

  def isReturn: Boolean = details != "success"
}

object AssembleRes {
  def apply(id: String, address: String, returnTo: String, startWhen: String, txSpec: String, txId: String, tx: String, details: String): AssembleRes = {
    new AssembleRes(id, address, returnTo, startWhen, txSpec, txId, tx, 0, Calendar.getInstance().getTimeInMillis, details)
  }

  def apply(req: AssemblyReq, tx: String, details: String): AssembleRes = {
    val txId = parse(tx).getOrElse(Json.Null).hcursor.downField("id").as[String].getOrElse(throw new Exception("the provided transaction is invalid"))
    new AssembleRes(req.id, req.address, req.returnTo, req.startWhen, req.txSpec, txId, tx, 0, Calendar.getInstance().getTimeInMillis, details)
  }
}

case class Archive(id: String, address: String, returnTo: String, txId: String, archiveTimestamp: Long, details: String) {
  def elapsedInSec: Long = (Calendar.getInstance().getTimeInMillis - archiveTimestamp) / 1000

  def isReturn: Boolean = details != "success"
}

object Archive {
  def apply(res: AssembleRes): Archive = {
    new Archive(res.id, res.address, res.returnTo, res.txId, Calendar.getInstance().getTimeInMillis, res.details)
  }
}
