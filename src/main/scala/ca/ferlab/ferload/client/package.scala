package ca.ferlab.ferload

package object client {

  implicit class OpsNum(val str: String) {
    def isNumeric: Boolean = scala.util.Try(str.toDouble).isSuccess
  }

  case class LineContent(filePointer: String, fileName: Option[String] = None, size: Option[Long] = None)

  case class ManifestContent(lines: Seq[LineContent], totalSize: Option[Long])

}
