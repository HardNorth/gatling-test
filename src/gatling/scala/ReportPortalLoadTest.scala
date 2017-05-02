import java.util.{Date, Properties, UUID}
import java.util.concurrent.TimeUnit

import com.epam.ta.reportportal.util.{LogEntryGenerator, PictureEntryGenerator}
import io.gatling.core.Predef._
import io.gatling.core.body.StringBody
import io.gatling.http.Predef._

import scala.concurrent.duration.{Duration, FiniteDuration}

/**
  * Created by Vadzim Hushchanskou on 19.03.2017.
  */
class ReportPortalLoadTest extends Simulation {

  val (usersFileName, textEventPause, pictureEventPause, numberOfLogEventsInTest, userNumber, testDurationMinutes,
  reportPortalBaseUrl, projectName)=
    try {
      val prop = new Properties()
      prop.load(ClassLoader.getSystemClassLoader.getResourceAsStream("test.properties"))

      (
        prop.getProperty("com.epam.ta.reportportal.test.load.users.file.name"),
        new Integer(prop.getProperty("com.epam.ta.reportportal.test.load.event.text.pause")).toLong,
        new Integer(prop.getProperty("com.epam.ta.reportportal.test.load.event.picture.pause")).toLong,
        new Integer(prop.getProperty("com.epam.ta.reportportal.test.load.event.number")).toInt,
        new Integer(prop.getProperty("com.epam.ta.reportportal.test.load.user.number")),
        new Integer(prop.getProperty("com.epam.ta.reportportal.test.load.duration")).toLong,
        prop.getProperty("com.epam.ta.reportportal.test.load.base.url"),
        prop.getProperty("com.epam.ta.reportportal.test.load.project.name")
        )
    } catch {
      case e: Exception =>
        e.printStackTrace()
        sys.exit(1)
    }

  val userFeeder = tsv(usersFileName).circular

  val textFeeder = Iterator.continually(Map("logEntry" -> LogEntryGenerator.next()))

  val imageFeeder = Iterator.continually(Map("logEntry" -> LogEntryGenerator.next(),
    "picture" -> PictureEntryGenerator.next(), "fileName" -> {UUID.randomUUID().toString + ".png"}))

  val postLog = feed(textFeeder, "Text Feeder").exec(http("Log Text Event").post("/").body(StringBody("${logEntry}"))).pause(Duration(textEventPause, TimeUnit.MILLISECONDS))

  val postImage = feed(imageFeeder, "Image Feeder").exec(http("Log Picture Event").post("/").body(ByteArrayBody("${logEntry}"))).pause(Duration(pictureEventPause, TimeUnit.MILLISECONDS))

  val scn = scenario("ReportPortal load test")
    .exec(session => session
      .set("projectName", projectName)
      .set("launchName", "LoadTestLaunch_" + UUID.randomUUID().toString)
      .set("startTime", System.currentTimeMillis()))
    .exec(feed(userFeeder)
    .exec(http("Create Test Launch").post("${projectName}/launch").body(ElFileBody("startLaunch.json"))
      .check(status.is(200), jsonPath("$._id").saveAs("launchId")))
    .exec(http("Create Test Suite").post("${projectName}/item").body(ElFileBody("startLaunch.json"))
      .check(status.is(200), jsonPath("$._id").saveAs("suiteId")))
    .exec(http("Create Test Item").get("${project_name}/item/${suiteId}"))
    .repeat(numberOfLogEventsInTest, "Log event") {
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
