/*
 * AssembleLya1de991d8.scala
 * (Miniaturen 15)
 *
 * Copyright (c) 2015-2017 Hanns Holger Rutz. All rights reserved.
 *
 * This software and music is published under the
 * Creative Commons Attribution-NonCommercial-NoDerivatives 4.0 International License
 * (CC BY-NC-ND 4.0)
 *
 * For further information, please contact Hanns Holger Rutz at
 * contact@sciss.de
 */

package de.sciss.min15

import de.sciss.file._

import scala.sys.process._

object AssembleLya1de991d8 extends App {
  val dirIn   = file("lyapunov_vid") / "image_out"
  val dirTmp  = file("lyapunov_vid") / "tmp"
  val dirOut  = file("videos")
  dirTmp.mkdir()
  val hash    = "1de991d8"
  val prefix  = s"lya_$hash"
  val fileOut = dirOut / s"phase_$hash.mp4"

  if (dirTmp.children.isEmpty) {
    def mkIn (frame: Int) = dirIn  / s"$prefix-$frame.png"
    def mkTmp(frame: Int) = dirTmp / s"$prefix-$frame.png"

    var frameCount  = 0
    val blackFrame  = 2

    def allocFrame(): Int = { frameCount += 1; frameCount }

    def link(in: Int, out: Int = allocFrame()): Unit = Seq("ln", "-rs", mkIn(in).path, mkTmp(out).path).!

    val totalOut  = 1550
    val chunk     = (totalOut - 2) / 2
    val totalIn   = 6050

    link(blackFrame)
    for (i <- 1 to chunk) link(i)
    for (i <- totalIn - chunk + 1 to totalIn) link(i)
    link(blackFrame)
  }

  if (!fileOut.exists) Seq("avconv",
    "-i", (dirTmp / s"$prefix-%d.png").path,
    "-c:v", "libx264",
    "-r", "25",
    "-preset", "veryslow",
    "-crf", "22",
    "-s:v", "1080x1080",
    "-aspect", "1:1",
    "-bufsize", "8000K",
    "-maxrate", "60000K",
    "-f", "mp4",
    fileOut.path).!
}
