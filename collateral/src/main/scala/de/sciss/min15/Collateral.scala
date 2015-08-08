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
import de.sciss.file._
import de.sciss.processor.Processor

import scala.concurrent.blocking

object Collateral {
  case class Config(fIn: File, fOut: File, firstFrame: Int, lastFrame: Int,
                    sizeIn: Int = 430, sizeOut: Int = 1080, noise: Int = 32, thresh: Int = 160)

  def main(args: Array[String]): Unit = {
    val fBase = file("collateral_vid")
    val c1 = Config(fIn  = fBase / "image_in"  / "collateral1-%d.png",
                    fOut = fBase / "image_out" / "coll1out-%d.png",
                    firstFrame = 231, lastFrame = 1780, thresh = 127, sizeIn = 320)
    // c1.fOut.parent.mkdirs()
    run(c1)
  }

  def run(config: Config): Unit = {
    import config._
    val p = Processor[Unit]("render") { self =>
      // val cropOp      = new CropImageFilter(145, 20, 430, 430)
      val resampleOp  = new ResampleOp(sizeOut, sizeOut)
      val noiseOp     = new NoiseFilter
      noiseOp.setAmount(noise)
      noiseOp.setMonochrome(true)
      val threshOp    = new ThresholdFilter(thresh)
      val dirIn       = fIn .parent
      val dirOut      = fOut.parent
      val childIn     = fIn .name
      val childOut    = fOut.name
      val numFrames   = lastFrame - firstFrame + 1
      val imgOut      = new BufferedImage(sizeOut, sizeOut, BufferedImage.TYPE_BYTE_BINARY)

      var frame = 0
      while (frame < numFrames) {
        val fIn1      = dirIn  / childIn .replace("%d", (frame + firstFrame).toString)
        val fOut1     = dirOut / childOut.replace("%d", (frame + 1         ).toString)

        if (!fOut1.exists()) blocking {
          // println(fIn1)
          val imgIn     = ImageIO.read(fIn1)
          val imgCrop   = cropImage(imgIn, 145 + (430 - sizeIn)/2, 20 + (430 - sizeIn)/2, sizeIn, sizeIn)
          val imgUp     = resampleOp.filter(imgCrop, null)
          val imgNoise  = noiseOp.filter(imgUp, null)
          threshOp.filter(imgNoise, imgOut)
          ImageIO.write(imgOut, "png", fOut1)
        }

        frame += 1
        self.progress = frame.toDouble / numFrames
        self.checkAborted()
      }
    }

    println("_" * 33)
    p.monitor(printResult = false)

    waitForProcessor(p)
  }

  def cropImage(src: BufferedImage, x: Int, y: Int, width: Int, height: Int): BufferedImage =
    src.getSubimage(x, y, width, height)
}
