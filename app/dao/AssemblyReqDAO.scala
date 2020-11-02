package dao

import javax.inject.{Inject, Singleton}
import models.AssemblyReq
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait AssemblyReqComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  class AssemblyReqTable(tag: Tag) extends Table[AssemblyReq](tag, "ASSEMBLY_REQ") {
    def id = column[String]("ID")

    def scanId = column[Int]("SCAN_ID")

    def address = column[String]("ADDRESS")

    def returnTo = column[String]("RETURN_TO")

    def startWhen = column[String]("START_WHEN")

    def txSpec = column[String]("TX_SPEC")

    def timestamp = column[Long]("TIMESTAMP")

    def * = (id, scanId, address, returnTo, startWhen, txSpec, timestamp) <> (AssemblyReq.tupled, AssemblyReq.unapply)
  }

}

@Singleton()
class AssemblyReqDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
  extends AssemblyReqComponent
    with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val requests = TableQuery[AssemblyReqTable]

  /**
   * inserts a request into db
   *
   * @param req AssemblyReq
   */
  def insert(req: AssemblyReq): Future[Unit] = db.run(requests += req).map(_ => ())

  /**
   * @param id request id
   * @return request associated with the id
   */
  def byId(id: String): Future[AssemblyReq] = db.run(requests.filter(req => req.id === id).result.head)

  /**
   * deletes by id
   *
   * @param id request id
   */
  def deleteById(id: String): Future[Int] = db.run(requests.filter(req => req.id === id).delete)
}