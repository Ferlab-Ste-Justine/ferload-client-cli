package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.ICommandLine

class ConsoleCommandLine extends ICommandLine {

  override def readLine(fmt: String): String = System.console.readLine(s"$fmt: ")

  override def readPassword(fmt: String): String = System.console.readPassword(s"$fmt: ").mkString
}
