import java.util.concurrent.TimeUnit
import java.util.{Properties, UUID}

import com.epam.ta.reportportal.util._
import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.collection.immutable.Map.Map2
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Random

/**
  * Created by Vadzim Hushchanskou on 19.03.2017.
  */
class ReportPortalLoadTest extends Simulation {

  val (usersFileName, textEventPause, pictureEventPause, numberOfLogEventsInTest, userNumber, testDurationMinutes,
  reportPortalBaseUrl, projectName) =
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

  private val headers: Map[String, String] = new Map2("Authorization", "bearer ${password}", "Content-Type", "application/json")

  private val userFeeder = csv(usersFileName).circular

  private val textFeeder = Iterator.continually(Map("logEntry" -> LogEntryGenerator.next(),
    "entryTime" -> System.currentTimeMillis()))

  private val imageFeeder = Iterator.continually(Map("logEntry" -> LogEntryGenerator.next(),
    "picture" -> PictureEntryGenerator.next(), "fileName" -> {
      UUID.randomUUID().toString + ".png"
    },
    "entryTime" -> System.currentTimeMillis()))

  private val postLog = feed(textFeeder, "Text Feeder").exec(http("Log Text Event").post("${project_name}/log")
    .body(ElFileBody("logEntry.json")).headers(headers)).pause(Duration(textEventPause, TimeUnit.MILLISECONDS))

  //TODO: convert to multipart
  private val postImage = feed(imageFeeder, "Image Feeder").exec(http("Log Picture Event").post("${project_name}/log")
    .bodyPart(ElFileBodyPart("logEntryImage.json").contentType("application/json"))
    .bodyPart(ElFileBodyPart("logEntryImage.json").fileName("${fileName}").contentType("image/png")).headers(headers)
    .asMultipartForm).pause(Duration(pictureEventPause, TimeUnit.MILLISECONDS))

  private val scn = scenario("ReportPortal load test")
    .feed(userFeeder)
    .exec(session => {
      session
        .set("projectName", projectName)
        .set("launchName", "LoadTestLaunch_" + UUID.randomUUID().toString)
        .set("startTime", System.currentTimeMillis())
        .set("suiteNumber", 1 + Random.nextInt(4))
        .set("testNumber", 10 + Random.nextInt(20))
        .set("testStepNumber", 3 + Random.nextInt(7))
        .set("entriesNumber", numberOfLogEventsInTest / (session("testNumber").as[Int] * session("suiteNumber").as[Int]))
    })
    .exec(
      exec(http("Create Test Launch").post("${projectName}/launch").body(ElFileBody("startLaunch.json")).headers(headers)
        .check(status.is(201), jsonPath("$.id").saveAs("launchId"))).exitHereIfFailed
        .repeat("${suiteNumber}", "Suite") {
          // Start Test Suite
          exec(session =>session.set("suiteName", SuiteName.name()).set("suiteStartTime", System.currentTimeMillis()))
          exec(http("Create Test Suite").post("${projectName}/item").body(ElFileBody("startSuite.json")).headers(headers)
            .check(status.is(200), jsonPath("$.id").saveAs("suiteId")))
          repeat("${entriesNumber}", "Log event") {
            exec(session => session.set("testStartTime", System.currentTimeMillis()).set("testName", TestName.name()))
            exec(http("Create Test").post("${project_name}/item/${suiteId}").body(ElFileBody("startTest.json")).headers(headers)
              .check(status.is(200), jsonPath("$.id").saveAs("testId")))
            exec(session => session.set("stepStartTime", System.currentTimeMillis()).set("testStepName", TestStepName.name()))
            randomSwitch(95.0 -> postLog, 5.0 -> postImage)
          }
        }
        .exec()
    )

  // Gatling starts by calling this method
  setUp(
    scn.inject(
      rampUsers(userNumber) over FiniteDuration(testDurationMinutes, TimeUnit.MINUTES)
    )
  ).maxDuration(FiniteDuration(testDurationMinutes + Math.round(testDurationMinutes * 0.1), TimeUnit.MINUTES))
    .protocols(http.baseURL(reportPortalBaseUrl))
}
