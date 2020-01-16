package com.epam.reportportal.test.performance

import java.util.concurrent.TimeUnit
import java.util.{Properties, UUID}

import com.epam.reportportal.test.performance.ReportPortalLoadTest.{basicScenario, reportPortalBaseUrl, testDurationMinutes, userNumber}
import com.epam.ta.reportportal.util._
import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import org.apache.commons.lang3.StringEscapeUtils

import scala.collection.immutable.Map.Map2
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Random

/**
 * Created by Vadzim Hushchanskou on 19.03.2017.
 */

object ReportPortalLoadTest {
  val IMAGE_GENERATORS: ThreadLocal[PictureEntryGenerator] =
    ThreadLocal.withInitial(() => new PictureEntryGenerator(320, 240))

  val PROPERTIES: Properties = try {
    val prop = new Properties()
    prop.load(ClassLoader.getSystemClassLoader.getResourceAsStream("test.properties"))
    prop
  } catch {
    case e: Exception =>
      e.printStackTrace()
      sys.exit(1)
  }

  val usersFileName: String = PROPERTIES.getProperty("com.epam.ta.reportportal.test.load.users.file.name")
  val textEventPause: Long = new Integer(PROPERTIES.getProperty("com.epam.ta.reportportal.test.load.event.text.pause")).toLong
  val pictureEventPause: Long = new Integer(PROPERTIES.getProperty("com.epam.ta.reportportal.test.load.event.picture.pause")).toLong
  val numberOfLogEventsInTest: Int = new Integer(PROPERTIES.getProperty("com.epam.ta.reportportal.test.load.event.number")).toInt
  val userNumber: Int = new Integer(PROPERTIES.getProperty("com.epam.ta.reportportal.test.load.user.number")).toInt
  val testDurationMinutes: Long = new Integer(PROPERTIES.getProperty("com.epam.ta.reportportal.test.load.duration")).toLong
  val reportPortalBaseUrl: String = PROPERTIES.getProperty("com.epam.ta.reportportal.test.load.base.url")
  val projectName: String = PROPERTIES.getProperty("com.epam.ta.reportportal.test.load.project.name")

  private val headers: Map[String, String] = new Map2("Authorization", "bearer ${password}", "Content-Type", "application/json")

  private val userFeeder = csv(usersFileName).circular

  private val textFeeder = Iterator.continually(Map("logEntry" -> StringEscapeUtils.escapeJava(LogEntryGenerator.next())))

  private val imageFeeder = Iterator.continually(Map("logEntry" -> StringEscapeUtils.escapeJava(LogEntryGenerator.next()),
    "picture" -> IMAGE_GENERATORS.get().next(), "fileName" -> {
      UUID.randomUUID().toString + ".png"
    }))

  private val postLog = feed(textFeeder).exec(http("Log Text Event").post("${projectName}/log")
    .body(ElFileBody("bodies/logEntry.json")).headers(headers)).pause(Duration(textEventPause, TimeUnit.MILLISECONDS))

  //TODO: convert to multipart
  private val postImage = feed(imageFeeder).exec(http("Log Picture Event").post("${projectName}/log")
    .bodyPart(ElFileBodyPart("json_request_part", "bodies/logEntryImage.json").contentType("application/json"))
    .bodyPart(ByteArrayBodyPart("binary_part", "${picture}").fileName("${fileName}").contentType("image/png")).headers(headers)
    .asMultipartForm).pause(Duration(pictureEventPause, TimeUnit.MILLISECONDS))


  // Entry Chain
  val entry =
    exec(session =>
      session
        .set("entryTime", System.currentTimeMillis())
    )
      .randomSwitch(95.0 -> postLog, 5.0 -> postImage)

  // Test Step Chain
  val testStep =
    exec(session =>
      session
        .set("testStepName", TestStepName.name())
        .set("stepStartTime", System.currentTimeMillis())
    )
      // Send Start Test Step Request
      .exec(http("Create Test Step").post("${projectName}/item/${testId}").body(ElFileBody("bodies/startTestStep.json"))
        .headers(headers).check(status.is(201), jsonPath("$.id").saveAs("testStepId")))
      // Log events
      .repeat("${itemsInTestStep}", "Log event") {
        entry
      }
      // Init Test Step Finish variables
      .exec(session =>
        session
          .set("stepStopTime", System.currentTimeMillis())
      )
      .exec(session =>
        session
          .set("itemStopDate", ServiceTimeFormat.format(session("stepStopTime").as[Long]))
      )
      // Send Finish Test Step Request
      .exec(http("Finish Test Step").put("${projectName}/item/${testStepId}").body(ElFileBody("bodies/stopItem.json"))
        .headers(headers).check(status.is(200)))

  // Test Chain
  val test: ChainBuilder =
    exec(session =>
      session
        .set("testName", TestName.name())
        .set("testStartTime", System.currentTimeMillis())
    )
      // Send Start Test Request
      .exec(http("Create Test").post("${projectName}/item/${suiteId}").body(ElFileBody("bodies/startTest.json"))
        .headers(headers).check(status.is(201), jsonPath("$.id").saveAs("testId")))

      // Start Test Step
      .repeat("${testStepNumber}", "Test Step") {
        testStep
      }

      // Init Test Finish variables
      .exec(session =>
        session
          .set("testStopTime", System.currentTimeMillis())
      )
      .exec(session =>
        session
          .set("itemStopDate", ServiceTimeFormat.format(session("testStopTime").as[Long]))
      )
      // Send Finish Test Request
      .exec(http("Finish Test").put("${projectName}/item/${testId}").body(ElFileBody("bodies/stopItem.json"))
        .headers(headers).check(status.is(200)))

  // Suite Chain
  val suite: ChainBuilder =
    exec(session =>
      session
        .set("suiteName", SuiteName.name())
        .set("suiteStartTime", System.currentTimeMillis())
    )
      // Send Start Suite Request
      .exec(http("Create Test Suite").post("${projectName}/item").body(ElFileBody("bodies/startSuite.json")).headers(headers)
        .check(status.is(201), jsonPath("$.id").saveAs("suiteId")))

      // Start Test
      .repeat("${testsInSuite}", "Test") {
        test
      }
      // Init Suite Finish variables
      .exec(session =>
        session
          .set("suiteStopTime", System.currentTimeMillis())
      )
      .exec(session =>
        session
          .set("itemStopDate", ServiceTimeFormat.format(session("suiteStopTime").as[Long]))
      )
      // Send Finish Test Request
      .exec(http("Finish Suite").put("${projectName}/item/${suiteId}").body(ElFileBody("bodies/stopItem.json"))
        .headers(headers).check(status.is(200)))

  // The scenario defined by this variable
  private val basicScenario = scenario("ReportPortal load test")
    .feed(userFeeder)

    // Init Global variables
    .exec(session =>
      session
        .set("projectName", projectName)
        .set("launchName", "LoadTestLaunch_" + UUID.randomUUID().toString)
        .set("startTime", System.currentTimeMillis())
        .set("itemsInTestStep", 1 + Random.nextInt(3))
        .set("testStepNumber", 3 + Random.nextInt(8))
        .set("suiteNumber", 1 + Random.nextInt(5))
    )
    .exec(session =>
      session
        .set("testNumber", (numberOfLogEventsInTest / session("itemsInTestStep").as[Int]) / session("testStepNumber").as[Int])
    )
    .exec(session =>
      session
        .set("testsInSuite", session("testNumber").as[Int] / session("suiteNumber").as[Int])
    )

    // Start Scenario
    // Start Launch
    .exec(http("Create Test Launch").post("${projectName}/launch").body(ElFileBody("bodies/startLaunch.json")).headers(headers)
      .check(status.is(201), jsonPath("$.id").saveAs("launchId"))).exitHereIfFailed

    // Start Test Suite
    .repeat("${suiteNumber}", "Suite") {
      suite
    }
    // Init Launch Finish variables
    .exec(session =>
      session
        .set("launchStopTime", System.currentTimeMillis())
    )
    .exec(session =>
      session
        .set("itemStopDate", ServiceTimeFormat.format(session("launchStopTime").as[Long]))
    )
    // Send Finish Launch Request
    .exec(http("Finish Suite").put("${projectName}/launch/${launchId}/finish").body(ElFileBody("bodies/stopItem.json"))
      .headers(headers).check(status.is(200)))
}

class ReportPortalLoadTest extends Simulation {

  // Gatling starts by calling this method
  setUp(
    basicScenario.inject(
      rampConcurrentUsers(0) to (userNumber) during FiniteDuration(testDurationMinutes, TimeUnit.MINUTES)
    )
  ).maxDuration(FiniteDuration(Math.round(TimeUnit.MINUTES.toMillis(testDurationMinutes) * 1.5), TimeUnit.MILLISECONDS))
    .protocols(http.baseUrl(reportPortalBaseUrl))
}
