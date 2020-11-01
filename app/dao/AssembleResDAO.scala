package dao

import javax.inject.{Inject, Singleton}
import models.AssembleRes
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait AssembleResComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import profile.api._

  class AssembleResTable(tag: Tag) extends Table[AssembleRes](tag, "ASSEMBLE_REQ") {
    def id = column[String]("ID")
    def address = column[String]("ADDRESS")
    def returnTo = column[String]("RETURN_TO")
    def startWhen = column[String]("START_WHEN")
    def txSpec = column[String]("TX_SPEC")
    def tx = column[String]("TX")
    def timestamp = column[Long]("TIMESTAMP")
    def * = (id, address, returnTo, startWhen, txSpec, tx, timestamp) <> (AssembleRes.tupled, AssembleRes.unapply)
  }
}

@Singleton()
class AssembleResDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
  extends AssembleResComponent
    with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val results = TableQuery[AssembleResTable]

  /**
   * inserts a request into db
   * @param req AssembleRes
   */
  def insert(req: AssembleRes): Future[Unit] = db.run(results += req).map(_ => ())

  /**
   * @param id request id
   * @return request associated with the id
   */
  def byId(id: String): Future[AssembleRes] = db.run(results.filter(req => req.id === id).result.head)

  /**
   * deletes by id
   * @param id request id
   */
  def deleteById(id: String): Future[Int] = db.run(results.filter(req => req.id === id).delete)
}