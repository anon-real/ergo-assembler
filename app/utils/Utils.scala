package utils

import java.io.{PrintWriter, StringWriter}

object Utils {
  def getStackTraceStr(e: Throwable): String = {
//    val sw = new StringWriter
//    val pw = new PrintWriter(sw)
//    e.printStackTrace(pw)
//    sw.toString
    e.getMessage
  }
}
