package ca.ferlab.ferload.client.clients.inf

trait ICommandLine {
  def readLine(fmt: String): String

  def readPassword(fmt: String): String
}
