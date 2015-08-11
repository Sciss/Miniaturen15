/*
 * Collateral.scala
 * (Miniaturen 15)
 *
 * Copyright (c) 2015 Hanns Holger Rutz. All rights reserved.
 *
 * This software and music is published under the
 * Creative Commons Attribution-NonCommercial-NoDerivatives 4.0 International License
 * (CC BY-NC-ND 4.0)
 *
 * For further information, please contact Hanns Holger Rutz at
 * contact@sciss.de
 */

package de.sciss.min15

import java.awt.image.BufferedImage
import javax.imageio.ImageIO

import com.jhlabs.image.{NoiseFilter, ThresholdFilter}
import com.mortennobel.imagescaling.ResampleOp
import de.sciss.dsp.Resample
import de.sciss.file._
import de.sciss.processor.Processor

import scala.concurrent.blocking

object Collateral {
  case class Config(fIn: File, fOut: File, firstFrame: Int, lastFrame: Int,
                    sizeIn: Int = 430, sizeOut: Int = 1080, noise: Int = 32, thresh: Int = 160,
                    resampleWindow: Int = 29)

  def main(args: Array[String]): Unit = {
    val fBase = file("collateral_vid")
//    val c1 = Config(fIn  = fBase / "image_in"  / "collateral1-%d.png",
//      fOut = fBase / "image_out" / "coll1out-%d.png",
//      firstFrame = 231, lastFrame = 1780, thresh = 127, sizeIn = 320)
//    run(c1)
    val c1 = Config(fIn  = fBase / "image_in"  / "collateral1-%d.png",
                    fOut = fBase / "image_out" / "coll1outR-%d.png",
                    firstFrame = 231, lastFrame = 1006 - 1 /* 1780 */, thresh = 127, sizeIn = 320)
    resample(c1)
  }

  def resample(config: Config): Unit = {
    import config._

    require(resampleWindow % 2 == 1, s"resampleWindow ($resampleWindow) must be odd")

    val p = Processor[Unit]("render") { self =>
      val resizeOp      = new ResampleOp(sizeOut, sizeOut)
      val noiseOp       = new NoiseFilter
      noiseOp.setAmount(noise)
      noiseOp.setMonochrome(true)
      val threshOp      = new ThresholdFilter(thresh)
      val dirIn         = fIn .parent
      val dirOut        = fOut.parent
      val childIn       = fIn .name
      val childOut      = fOut.name
      val numInFrames   = lastFrame - firstFrame + 1
      val numOutFrames  = numInFrames * 2
      val imgOut        = new BufferedImage(sizeOut, sizeOut, BufferedImage.TYPE_BYTE_BINARY)

      def mkFIn (x: Int): File = dirIn  / childIn .replace("%d", (x + firstFrame).toString)
      def mkFOut(x: Int): File = dirOut / childOut.replace("%d", (x + 1).toString)

      def readFrame(x: Int): BufferedImage = {
        val fIn1      = mkFIn(x)
        val imgIn     = ImageIO.read(fIn1)
        val imgCrop   = cropImage(imgIn, 145 + (430 - sizeIn)/2, 20 + (430 - sizeIn)/2, sizeIn, sizeIn)
        imgCrop
      }

      // e.g. resampleWindow = 5, winH = 2 ; LLLRR
      val winH = resampleWindow / 2

      var frame0      = readFrame(0)
      val widthIn     = frame0.getWidth
      val heightIn    = frame0.getHeight

      assert (widthIn == sizeIn && heightIn == sizeIn)

      val frameWindow = Array.tabulate(resampleWindow) { i =>
        val j = i - winH
        if (j <= 0) frame0 else readFrame(j)
      }

      frame0 = null // let it be GC'ed

      val resample    = Resample(Resample.Quality.Medium /* Low */)
      val imgRsmp     = Array.fill(2)(new BufferedImage(widthIn, heightIn, BufferedImage.TYPE_BYTE_GRAY))
      val bufRsmpIn   = new Array[Float](resampleWindow)
      val bufRsmpOut  = new Array[Float](2)

      def performResample(): Unit = {
        var y = 0
        while (y < heightIn) {
          var x = 0
          while (x < widthIn) {
            var t = 0
            while (t < resampleWindow) {
              val rgbIn = frameWindow(t).getRGB(x, y)
              val vIn = (((rgbIn & 0xFF0000) >> 16) + ((rgbIn & 0x00FF00) >> 8) + (rgbIn & 0x0000FF)) / 765f // it's gray anyway
              bufRsmpIn(t) = vIn
              t += 1
            }
            resample.process(src = bufRsmpIn, srcOff = winH, dest = bufRsmpOut, destOff = 0, length = 2, factor = 2)
            var off = 0
            while (off < 2) {
              // note: gain factor 2 here!
              val vOut    = (math.max(0f, math.min(1f, bufRsmpOut(off) * 2)) * 255 + 0.5f).toInt
              val rgbOut  = 0xFF000000 | (vOut << 16) | (vOut << 8) | vOut
              imgRsmp(off).setRGB(x, y, rgbOut)
              off += 1
            }
            x += 1
          }
          y += 1
        }
      }

      var frameIn  = resampleWindow - winH
      var frameOut = 0
      while (frameOut < numOutFrames) {
        val fOut1 = mkFOut(frameOut)
        val fOut2 = mkFOut(frameOut + 1)

        if (!fOut1.exists() || !fOut2.exists()) blocking {
          performResample()
          var off = 0
          while (off < 2) {
            val imgCrop   = imgRsmp(off)
            val imgUp     = resizeOp.filter(imgCrop, null)
            val imgNoise  = noiseOp.filter(imgUp, null)
            threshOp.filter(imgNoise, imgOut)
            ImageIO.write(imgOut, "png", if (off == 0) fOut1 else fOut2)
            off += 1
          }
        }

        // handle overlap
        System.arraycopy(frameWindow, 1, frameWindow, 0, resampleWindow - 1)
        if (frameIn < numInFrames) {
          frameWindow(resampleWindow - 1) = readFrame(frameIn)
        }

        frameIn  += 1
        frameOut += 2
        self.progress = frameIn.toDouble / numInFrames
        self.checkAborted()
      }
    }

    println("_" * 33)
    p.monitor(printResult = false)

    waitForProcessor(p)
  }
}
