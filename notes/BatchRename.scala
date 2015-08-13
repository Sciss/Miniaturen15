import de.sciss.file._
import scala.sys.process._

file("/home/hhrutz/Documents/devel/Miniaturen15/collateral_vid/image_out")
  .children.foreach { c =>
  if (c.name.startsWith("collat_439fa6b9-")) {
    val b = c.base
    val f = b.substring(16).toInt
    Seq("mv", c.path, (c.parent / s"collat_521f9faf-$f.png").path).!
  }
}