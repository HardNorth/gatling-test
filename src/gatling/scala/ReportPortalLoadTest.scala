import io.gatling.core.Predef._

/**
  * Created by delgr on 19.03.2017.
  */
class ReportPortalLoadTest extends Simulation {

  val usersFileName = "users.tsv"
  val userFeeder = tsv(usersFileName).circular


}
