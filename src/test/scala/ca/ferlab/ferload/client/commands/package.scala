package ca.ferlab.ferload.client

import org.json.JSONObject
import picocli.CommandLine
import picocli.CommandLine.IExecutionExceptionHandler

import java.util

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

  val mockFerloadConfigPassword: JSONObject = {
    val config = new util.HashMap[String, String]()
    config.put("url", "http://keycloak")
    config.put("realm", "abc")
    config.put("client-id", "123")
    config.put("audience", "456")
    new JSONObject()
      .put("keycloak", new JSONObject(config))
  }

  val mockFerloadConfigToken: JSONObject = {
    new JSONObject()
      .put("method", "token")
      .put("tokenConfig", new JSONObject()
        .put("realm", "abc")
        .put("client-id", "123")
        .put("link", "link_to_token")
        .put("helper", "helper_text"))
  }

  val mockFerloadConfigDevice: JSONObject = {
    new JSONObject()
      .put("method", "device")
      .put("keycloak", new JSONObject()
        .put("url", "http://keycloak")
        .put("realm", "abc")
        .put("audience", "456")
        .put("device-client", "789"))
  }
}
