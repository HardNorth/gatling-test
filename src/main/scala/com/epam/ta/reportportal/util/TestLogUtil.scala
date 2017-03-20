/*
 * Copyright (C) 2017 Epic Games, Inc. All Rights Reserved.
 */

package com.epam.ta.reportportal.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import org.apache.commons.lang3.{RandomStringUtils, StringUtils}
import org.apache.commons.math3.distribution.GammaDistribution

object LogEntryGenerator extends Iterator[String] {
  val logTimeStampFormat = "HH:mm:ss.SSS"
  val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 1234567890 - _:<>&\"'=+/\\,.$\t\n"

  private val uniqueIdCounter: AtomicLong = new AtomicLong()

  private val threadIdCounter: AtomicLong = new AtomicLong()

  private val threads = new ConcurrentHashMap[Long, Long]()

  private val random = new GammaDistribution(2, 2)

  override def hasNext: Boolean = true

  override def next(): String = {
    val threadId = Thread.currentThread().getId
    val threadNumber = {
      if (threads.containsKey(threadId)) {
        threads.get(threadId)
      }
      else {
        val number = threadIdCounter.getAndIncrement()
        threads.put(threadId, number)
        number
      }
    }

    val sdf: SimpleDateFormat = new SimpleDateFormat(logTimeStampFormat)
    StringUtils.join(sdf.format(new Date()), " [Test thread (", threadNumber, ")] DEBUG TC", threadNumber,
      "_VerifyTextLogging - ", uniqueIdCounter.getAndIncrement(), " - ",
      RandomStringUtils.random(Math.round(random.sample() * 32).toInt, alphabet))
  }
}