package de.sciss.min15

import de.sciss.file._

import scala.sys.process._

// avconv v0.8.x does not support `-start_number` option,
// so we symlink to temp dir
object AssembleCollat7a510609 extends App {
  val dirIn   = file("collateral_vid") / "image_out"
  val dirTmp  = file("collateral_vid") / "tmp"
  val dirOut  = file("videos")
  dirTmp.mkdir()
  val prefix  = "collat_7a510609"
  val fileOut = dirOut / s"$prefix.mp4"

  if (dirTmp.children.isEmpty) {
    def mkIn (frame: Int) = dirIn  / s"$prefix-$frame.png"
    def mkTmp(frame: Int) = dirTmp / s"$prefix-$frame.png"

    var frameCount  = 0

    def allocFrame(): Int = { frameCount += 1; frameCount }

    def link(in: Int, out: Int = allocFrame()): Unit = Seq("ln", "-rs", mkIn(in).path, mkTmp(out).path).!

    val totalOut  = 1550
    val totalIn   = 1552

    for (i <- totalIn - totalOut + 1 to totalIn) link(i)
  }

  if (!fileOut.exists) Seq("avconv",
    "-i", (dirTmp / s"$prefix-%d.png").path,
    "-vcodec", "libxvid",
    "-r", "25",
    "-q", "100",
    "-pass", "1",
    "-vf", "scale=1080:1080,fade=type=in:start_frame=0:nb_frames=12",
    "-aspect", "1:1",
    "-vb", "6M",
    "-threads", "0",
    "-f", "mp4",
    fileOut.path).!
}
