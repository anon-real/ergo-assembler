package models

import java.util.Calendar

import io.circe.Json


case class AssemblyReq(address: String, startWhen: String, txSpec: String, requestTimestamp: Long) {
  def elapsedInSec: Long = (Calendar.getInstance().getTimeInMillis - requestTimestamp) / 1000
}

object AssemblyReq {
  def apply(reqJs: Json): AssemblyReq = {
    val address = reqJs.hcursor.downField("address").as[String].getOrElse(throw new Exception("address field is required"))
    val startWhen = reqJs.hcursor.downField("startWhen").as[Json].getOrElse(throw new Exception("startWhen field is required")).noSpaces
    val txSpec = reqJs.hcursor.downField("txSpec").as[Json].getOrElse(throw new Exception("txSpec field is required")).noSpaces
    val timestamp = Calendar.getInstance().getTimeInMillis
    new AssemblyReq(address, startWhen, txSpec, timestamp)
  }
}

case class AssembleRes(address: String, startWhen: String, txSpec: String, tx: String, isReturn: Boolean, confNum: Int, assembleTimestamp: Long) {
  def elapsedInSec: Long = (Calendar.getInstance().getTimeInMillis - assembleTimestamp) / 1000

  def toReq: AssemblyReq = new AssemblyReq(address, startWhen, txSpec, Calendar.getInstance().getTimeInMillis)

  def isConfirmed: Boolean = confNum > 0
}

object AssembleRes {
  def apply(address: String, startWhen: String, txSpec: String, tx: String, isReturn: Boolean): AssembleRes = {
    new AssembleRes(address, startWhen, txSpec, tx, isReturn, 0, Calendar.getInstance().getTimeInMillis)
  }

  def apply(req: AssemblyReq, tx: String, isReturn: Boolean): AssembleRes = {
    new AssembleRes(req.address, req.startWhen, req.txSpec, tx, isReturn, 0, Calendar.getInstance().getTimeInMillis)
  }
}
