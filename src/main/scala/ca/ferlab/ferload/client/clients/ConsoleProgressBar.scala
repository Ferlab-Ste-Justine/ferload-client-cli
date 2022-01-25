package ca.ferlab.ferload.client.clients

import org.apache.commons.lang3.StringUtils

object ConsoleProgressBar {

  private val oneKB = 1024
  private val oneMB = oneKB * 1024
  private val formatSize = "%6d / %6d %s"
  private val formatBar = "\r%s [%s%s] %s (%3d%%) "
  private val packageEmoji = new String(Character.toChars(0x1F4E6))

  // remember the last progressbar name to detect when to print line returns
  private var lastProgressBar = ""
  private var lastPercents: Map[String, Double] = Map()

  // the files we are working on are worth at least MB
  private def formatSize(value: Long, total: Long): String = {
    if (total < oneKB) {
      String.format(formatSize, value.toInt, total, "B ")
    } else if (total < oneMB) {
      String.format(formatSize, (value / oneKB).toInt, (total / oneKB).toInt, "KB")
    } else {
      String.format(formatSize, (value / oneMB).toInt, (total / oneMB).toInt, "MB")
    }
  }

  def displayProgressBar(name: String, padding: Int, value: Long, total: Long, size: Int = 50, displayForEveryPercent: Int = 10): Unit = {

    // we want to avoid multiple threads to print at the same time
    synchronized {
      val lastPercent = lastPercents.getOrElse(name, 0.0)
      val percent = (value * 100.0) / total

      // this condition avoid to much print in the console
      if (((percent - lastPercent) >= displayForEveryPercent) || percent == 100.0) {

        if (StringUtils.isNotBlank(lastProgressBar) && !name.equals(lastProgressBar)) {
          println
        }

        lastProgressBar = name;
        lastPercents = lastPercents + (name -> percent)

        val done = "#" * ((percent * size) / 100).toInt
        val remaining = " " * (size - done.length)

        printf(formatBar, StringUtils.rightPad(name, padding), done, remaining, formatSize(value, total), percent.toInt)

        if (value == total) {
          print(packageEmoji)
        }
      }
    }
  }
}