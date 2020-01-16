package com.epam.ta.reportportal.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ConcurrentHashMap, ThreadLocalRandom}

import org.apache.commons.lang3.{RandomStringUtils, StringUtils}
import org.apache.commons.math3.distribution.GammaDistribution


private object Random {
  private val gammaDistribution = new GammaDistribution(2, 2)

  def gamma(): Double = gammaDistribution.sample()
}

private object Counter {
  private val threadIdCounter: AtomicLong = new AtomicLong()

  private val threads = new ConcurrentHashMap[Long, Long]()

  def threadNumber(): Long = {
    val threadId = Thread.currentThread().getId
    if (threads.containsKey(threadId)) {
      threads.get(threadId)
    }
    else {
      val number = threadIdCounter.getAndIncrement()
      threads.put(threadId, number)
      number
    }
  }

  def uniqueId(): String = StringUtils.join(System.currentTimeMillis(), "-", threadNumber(), "-", ThreadLocalRandom.current().nextInt(100000))
}

object SuiteName {
  def name(): String = {
    val threadNumber = Counter.threadNumber()
    StringUtils.join("Load Test Suite (", threadNumber, ") ", Counter.uniqueId())
  }
}

object TestName {
  def name(): String = {
    val threadNumber = Counter.threadNumber()
    StringUtils.join("Test (", threadNumber, ") ", Counter.uniqueId())
  }
}

object TestStepName {
  def name(): String = {
    val threadNumber = Counter.threadNumber()
    StringUtils.join("Test Step (", threadNumber, ") ", Counter.uniqueId())
  }
}

object ServiceTimeFormat {
  val logTimeStampFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSX"

  private val formats = new ConcurrentHashMap[Long, SimpleDateFormat]()

  def format(time: Long): String = {
    val threadNumber = Counter.threadNumber()
    val sdf: SimpleDateFormat = formats.computeIfAbsent(threadNumber, new java.util.function.Function[Long, SimpleDateFormat]() {
      @Override
      def apply(key: Long): SimpleDateFormat = {
        new SimpleDateFormat(logTimeStampFormat)
      }
    })
    sdf.format(new Date(time))
  }
}

private object LogEntry {
  val logTimeStampFormat = "HH:mm:ss.SSS"

  private val formats = new ConcurrentHashMap[Long, SimpleDateFormat]()

  def logEntry(payload: String): String = {
    val threadNumber = Counter.threadNumber()
    val sdf: SimpleDateFormat = formats.computeIfAbsent(threadNumber, new java.util.function.Function[Long, SimpleDateFormat]() {
      @Override
      def apply(key: Long): SimpleDateFormat = {
        new SimpleDateFormat(logTimeStampFormat)
      }
    })

    StringUtils.join(sdf.format(new Date()), " [Test thread (", threadNumber, ")] DEBUG TC", threadNumber,
      "_VerifyTextLogging - ", Counter.uniqueId(), " - ", payload)
  }
}

object LogEntryGenerator extends Iterator[String] {

  val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 1234567890 - _:<>&\"'=+/\\,.$\t\n"

  override def hasNext: Boolean = true

  override def next(): String = {
    LogEntry.logEntry(RandomStringUtils.random(Math.round(Random.gamma() * 32).toInt, alphabet))
  }
}
