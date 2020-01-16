package com.epam.ta.reportportal.util

import java.io.{InputStreamReader, Reader}

import org.apache.commons.csv.{CSVFormat, CSVParser}

class CsvFeeder(val filePath: String, val batchSize: Int = 1000, val unlockThreshold: Double = 0.6) {

  private def getData: Seq[Map[String, String]] = {
    val in: Reader = new InputStreamReader(Thread.currentThread().getContextClassLoader.getResourceAsStream(filePath))
    val csvIterable: CSVParser = CSVFormat.DEFAULT.parse(in)
    val headers: java.util.List[java.lang.String] = csvIterable.getHeaderNames
    val resultBuilder = Seq.newBuilder[Map[String, String]]
    csvIterable.forEach(r => {
      val rowBuilder = Map.newBuilder[String, String]
      headers.forEach(h => rowBuilder += ((h, r.get(h))))
    })
    resultBuilder.result()
  }

  private val data: Seq[Map[String, String]] = getData

  private def fill(queue: java.util.concurrent.ConcurrentLinkedQueue[Map[String, String]],
                   source: Iterator[Map[String, String]], number: Int): Unit = {
    for (_ <- Range(0, number)) {
      queue.add(source.next())
    }
  }

  def circular: Iterator[Map[String, String]] = new scala.collection.Iterator[Map[String, String]] {
    private val circle = new java.util.concurrent.ConcurrentLinkedQueue[Map[String, String]]()
    private val mutex: Iterator[Map[String, String]] = Iterator.continually(data).flatten

    override def hasNext: Boolean = true

    override def next(): Map[String, String] = {
      var result = circle.poll()
      if (result == null) {
        val beforeUnlock = (batchSize * unlockThreshold).toInt
        val afterUnlock = batchSize - beforeUnlock
        mutex.synchronized {
          result = circle.poll()
          if (result == null) {
            fill(circle, mutex, beforeUnlock)
          }
        }
        if (result == null) {
          fill(circle, mutex, afterUnlock)
          result = circle.poll()
        }
      }
      result
    }
  }
}
