package de.sciss.min15

import de.sciss.file._

import scala.sys.process._

object AssembleLya1de991d8 extends App {
  val dirIn   = file("lyapunov_vid") / "image_out"
  val dirTmp  = file("lyapunov_vid") / "tmp"
  val dirOut  = file("videos")
  dirTmp.mkdir()
  val prefix  = "lya_1de991d8"
  val fileOut = dirOut / s"$prefix.mp4"

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
    "-vcodec", "libxvid",
    "-r", "25",
    "-q", "100",
    "-pass", "1",
    "-vf", "scale=1080:1080",
    "-aspect", "1:1",
    "-vb", "6M",
    "-threads", "0",
    "-f", "mp4",
    fileOut.path).!
}
