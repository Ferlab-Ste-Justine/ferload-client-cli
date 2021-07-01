package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.ICommandLine

import java.io.Console
import java.util.Scanner

class ConsoleCommandLine extends ICommandLine {

  // System.console support readPassword which is nice, but if not available use System.in
  val defaultConsole: Option[Console] = Option(System.console)
  val fallbackConsole = new Scanner(System.in)

  override def readLine(fmt: String): String = defaultConsole.map(_.readLine(s"$fmt: "))
    .getOrElse(fallbackReadLine(fmt))

  override def readPassword(fmt: String): String = defaultConsole.map(_.readPassword(s"$fmt: ").mkString)
    .getOrElse(fallbackReadLine(fmt))

  private def fallbackReadLine(fmt: String): String = {
    print(s"$fmt: ")
    fallbackConsole.nextLine()
  }

}
