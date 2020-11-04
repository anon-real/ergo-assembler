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
    def id = column[String]("ID", O.PrimaryKey)

    def scanId = column[Int]("SCAN_ID")

    def returnTo = column[String]("RETURN_TO")

    def tx = column[String]("TX")

    def timestamp = column[Long]("TIMESTAMP")

    def detail = column[String]("DETAIL")

    def * = (id, scanId, returnTo, tx.?, timestamp, detail) <> (ReqSummary.tupled, ReqSummary.unapply)
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
   * deletes all entities created before the timestamp
   *
   * @param timestamp to remove before this time
   * @return # of deleted
   */
  def beforeTime(timestamp: Long): Future[Seq[ReqSummary]] = db.run(results.filter(req => req.timestamp <= timestamp).result)

  /**
   * updates tx and detail field of the summary
   *
   * @param id     id
   * @param tx     transaction
   * @param detail detail
   * @return number of updated rows
   */
  def partialUpdate(id: String, tx: String, detail: String): Future[Int] = {
    val query = for {
      m <- results if m.id === id
    } yield (m.tx, m.detail)
    db.run(query.update(tx, detail))
  }

  /**
   * deletes by id
   *
   * @param id request id
   */
  def deleteById(id: String): Future[Int] = db.run(results.filter(req => req.id === id).delete)

  //  def upsert(req: ReqSummary): Future[Unit] = db.run(results.insertOrUpdate(req).map(_ => ()))
}