package ca.ferlab.ferload.client

import picocli.CommandLine
import picocli.CommandLine.IExecutionExceptionHandler

package object commands {

  def assertCommandException(commandLine: CommandLine, message: String, args: String*): Unit = {
    var exceptionOccurred = false
    commandLine.setExecutionExceptionHandler(new IExecutionExceptionHandler {
      override def handleExecutionException(ex: Exception, commandLine: CommandLine, parseResult: CommandLine.ParseResult): Int = {
        ex.printStackTrace()
        exceptionOccurred = ex.getMessage.contains(message)
        -1
      }
    })
    commandLine.execute(args: _*)
    assert(exceptionOccurred)
  }
}
