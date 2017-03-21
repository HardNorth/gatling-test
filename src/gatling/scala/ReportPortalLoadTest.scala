import java.util.concurrent.TimeUnit

import com.epam.ta.reportportal.util.{LogEntryGenerator, PictureEntryGenerator}
import io.gatling.core.Predef._
import io.gatling.core.body.StringBody
import io.gatling.http.Predef._

import scala.concurrent.duration.{Duration, FiniteDuration}

/**
  * Created by delgr on 19.03.2017.
  */
class ReportPortalLoadTest extends Simulation {

  val usersFileName = "users.tsv"
  val userFeeder = tsv(usersFileName).circular

  val textEventPause = 10

  val pictureEventPause = 100

  val userNumber = 1

  val testDurationMinutes = 1

  val reportPortalBaseUrl = "/"

  val postLog = exec(http("Log Text Event").post("/").body(StringBody(LogEntryGenerator.next()))).pause(Duration(textEventPause, TimeUnit.MILLISECONDS))

  val postImage = exec(http("Log Picture Event").post("/").body(ByteArrayBody(PictureEntryGenerator.next()))).pause(Duration(pictureEventPause, TimeUnit.MILLISECONDS))

  val scn = scenario("ReportPortal load test").exec(feed(userFeeder)
    .exec(http("Authenticate").get("/"))
    .exec(http("Create Launch").get("/"))
    .exec(http("Create Test Suite").get("/"))
    .exec(http("Create Test Item").get("/"))
    .repeat(1050, "Log event")
    {
      randomSwitch(95.0 -> postLog, 5.0 -> postImage)
    }
  )

  // Gatling starts by calling this method
  setUp(
    scn.inject(
      rampUsers(userNumber) over FiniteDuration(testDurationMinutes, TimeUnit.MINUTES)
    )
  ).maxDuration(FiniteDuration(testDurationMinutes + Math.round(testDurationMinutes * 0.1), TimeUnit.MINUTES))
    .protocols(http.baseURL(reportPortalBaseUrl))
}
