import java.util.Properties
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

  val (usersFileName, textEventPause, pictureEventPause, numberOfLogEventsInTest, userNumber, testDurationMinutes, reportPortalBaseUrl)=
    try {
      val prop = new Properties()
      prop.load(ClassLoader.getSystemClassLoader.getResourceAsStream(System.getProperty("env") + ".properties"))

      (
        prop.getProperty("com.epam.ta.reportportal.test.load.users.file.name"),
        new Integer(prop.getProperty("com.epam.ta.reportportal.test.load.event.text.pause")).toLong,
        new Integer(prop.getProperty("com.epam.ta.reportportal.test.load.event.picture.pause")).toLong,
        new Integer(prop.getProperty("com.epam.ta.reportportal.test.load.event.number")).toInt,
        new Integer(prop.getProperty("com.epam.ta.reportportal.test.load.user.number")),
        new Integer(prop.getProperty("com.epam.ta.reportportal.test.load.test.duration")).toLong,
        prop.getProperty("com.epam.ta.reportportal.test.load.test.base.url")
        )
    } catch {
      case e: Exception =>
        e.printStackTrace()
        sys.exit(1)
    }

  val userFeeder = tsv(usersFileName).circular

  val textFeeder = Iterator.continually(Map("logEntry" -> LogEntryGenerator.next()))

  val imageFeeder = Iterator.continually(Map("logEntry" -> PictureEntryGenerator.next()))

  val postLog = feed(textFeeder, "Text Feeder").exec(http("Log Text Event").post("/").body(StringBody("${logEntry}"))).pause(Duration(textEventPause, TimeUnit.MILLISECONDS))

  val postImage = feed(imageFeeder, "Image Feeder").exec(http("Log Picture Event").post("/").body(ByteArrayBody("${logEntry}"))).pause(Duration(pictureEventPause, TimeUnit.MILLISECONDS))

  val scn = scenario("ReportPortal load test").exec(feed(userFeeder)
    .exec(http("Authenticate").get("/"))
    .exec(http("Create Launch").get("/"))
    .exec(http("Create Test Suite").get("/"))
    .exec(http("Create Test Item").get("/"))
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
