package com.epam.ta.reportportal.util

import java.awt.{Color, Font, Rectangle, Shape}
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream

import javax.imageio.ImageIO
import org.apache.commons.lang3.RandomUtils.nextInt

class PictureEntryGenerator(val imageWidth: Int = 1366, val imageHeight: Int = 768) extends Iterator[Array[Byte]] {
  private val backgroundColor = new Color(255, 255, 255)
  private val fontColor = new Color(0, 0, 0)
  val minColor = 0
  val maxColor = 256
  val imageRect = new Rectangle(0, 0, imageWidth, imageHeight)

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
        val x1 = nextInt(0, imageWidth + 1)
        val y1 = nextInt(0, imageHeight + 1)
        val x2 = nextInt(0, imageWidth + 1)
        val y2 = nextInt(0, imageHeight + 1)
        val x = Math.min(x1, x2)
        val y = Math.min(y1, y2)
        val width = Math.max(x1, x2) - x
        val height = Math.max(y1, y2) - y
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
