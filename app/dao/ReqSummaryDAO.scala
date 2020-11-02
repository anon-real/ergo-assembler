package dao

import javax.inject.{Inject, Singleton}
import models.ReqSummary
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait ReqSummaryComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  class ReqSummaryTable(tag: Tag) extends Table[ReqSummary](tag, "SUMMARY") {
    def id = column[String]("ID")

    def scanId = column[Int]("SCAN_ID")

    def returnTo = column[String]("RETURN_TO")

    def txId = column[String]("TX_ID")

    def timestamp = column[Long]("TIMESTAMP")

    def detail = column[String]("DETAIL")

    def * = (id, scanId, returnTo, txId.?, timestamp, detail) <> (ReqSummary.tupled, ReqSummary.unapply)
  }

}

@Singleton()
class ReqSummaryDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
  extends ReqSummaryComponent
    with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val results = TableQuery[ReqSummaryTable]

  /**
   * inserts a request into db
   *
   * @param req ReqSummary
   */
  def insert(req: ReqSummary): Future[Unit] = db.run(results += req).map(_ => ())

  /**
   * @param id request id
   * @return request associated with the id
   */
  def byId(id: String): Future[ReqSummary] = db.run(results.filter(req => req.id === id).result.head)

  /**
   * deletes by id
   *
   * @param id request id
   */
  def deleteById(id: String): Future[Int] = db.run(results.filter(req => req.id === id).delete)
}