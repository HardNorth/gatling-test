package com.epam.ta.reportportal.util

import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.awt._
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO

import org.apache.commons.lang3.RandomUtils._
import org.apache.commons.lang3.{RandomStringUtils, StringUtils}
import org.apache.commons.math3.distribution.GammaDistribution


private object Random {
  private val gammaDistribution = new GammaDistribution(2, 2)

  def gamma(): Double = gammaDistribution.sample()
}

private object Counter {
  private val uniqueIdCounter: AtomicLong = new AtomicLong()

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

  def uniqueId(): Long = uniqueIdCounter.getAndIncrement()
}

private object LogEntry
{
  val logTimeStampFormat = "HH:mm:ss.SSS"

  private val formats = new ConcurrentHashMap[Long, SimpleDateFormat]()

  def logEntry(payload: String): String = {
    val threadNumber = Counter.threadNumber()

    val threadId = Thread.currentThread().getId
    val sdf: SimpleDateFormat = {
      if (formats.containsKey(threadId)) {
        formats.get(threadId)
      }
      else {
        val threadSdf = new SimpleDateFormat(logTimeStampFormat)
        formats.put(threadId, threadSdf)
        threadSdf
      }
    }

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

object PictureEntryGenerator extends Iterator[Array[Byte]] {
  private val backgroundColor = new Color(255,255,255)
  private val fontColor = new Color(0,0,0)
  val imageWidth = 1366
  val imageHeight = 768
  val minColor = 0
  val maxColor = 256
  val imageRect = new Rectangle(0,0,imageWidth, imageHeight)

  override def hasNext: Boolean = true

  override def next(): Array[Byte] = {
    val numberOfFigures = Math.round(2 * Random.gamma()).toInt
    val circles = nextInt(0, numberOfFigures)
    val rectangles = numberOfFigures - circles
    val image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.setColor(backgroundColor)
    graphics.fill(imageRect)

    for (i <- 0 to numberOfFigures) {
      var shape: Shape = null
      if (i < rectangles) {
        val x = nextInt(0, imageWidth)
        val y = nextInt(0, imageHeight)
        val width = nextInt(x, imageWidth + 1)
        val height = nextInt(y, imageHeight + 1)
        shape = new Rectangle(x, y, width, height)
      } else {
        val x = nextInt(0, imageWidth)
        val y = nextInt(0, imageHeight)
        val d = nextInt(1, Math.min(imageWidth - x, imageHeight - y))
        shape = new Ellipse2D.Float(x, y, d, d)
      }
      graphics.setColor(new Color(nextInt(minColor, maxColor), nextInt(minColor, maxColor), nextInt(minColor, maxColor)))
      graphics.fill(shape)
    }
    val s = LogEntry.logEntry("PNG Image")
    graphics.setColor(fontColor)
    graphics.setFont(new Font("Serif", Font.BOLD, 20))
    val fm = graphics.getFontMetrics()
    val y = fm.getHeight
    graphics.drawString(s, 10, y)
    graphics.dispose()

    val baos = new ByteArrayOutputStream()
    ImageIO.write(image, "png", baos)
    baos.toByteArray
  }
}