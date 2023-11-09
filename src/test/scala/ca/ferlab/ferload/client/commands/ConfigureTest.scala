package ca.ferlab.ferload.client.commands

import ca.ferlab.ferload.client.clients.inf.{ICommandLine, IFerload}
import ca.ferlab.ferload.client.configurations._
import com.typesafe.config.{Config, ConfigFactory}
import org.json.JSONObject
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import picocli.CommandLine

import java.io.File
import java.util

class ConfigureTest extends AnyFunSuite with BeforeAndAfter {

  val appTestConfig: Config = ConfigFactory.load.getObject("ferload-client").toConfig
  val path: String = File.createTempFile(".ferload-client", ".properties").getAbsolutePath
  val mockUserConfig = new UserConfig(path)
  val mockCommandLineInf: ICommandLine = new ICommandLine {
    override def readLine(fmt: String): String = {
      val mock = fmt.trim match {
        case "Ferload url" => "http://ferload"
        case "username" => "foo"
        case "username [foo]" => "foo (existing)" // change the existing value on purpose to assert the test
        case _ => fail(s"$fmt isn't mocked")
      }
      mock
    }

    override def readPassword(fmt: String): String = ???
  }

  val mockCommandLineTokenInf: ICommandLine = new ICommandLine {
    override def readLine(fmt: String): String = {
      val mock = fmt.trim match {
        case "Ferload url" => "http://ferload"
        case "Paste token here" => "aaa.bbb.ccc"
        case _ => fail(s"$fmt isn't mocked")
      }
      mock
    }

    override def readPassword(fmt: String): String = ???
  }

  val mockFerloadInf: IFerload = new IFerload {
    override def getConfig: JSONObject = mockFerloadConfigPassword

    override def getDownloadLinks(token: String, manifestContent: String): Map[String, String] = ???
  }

  val mockFerloadTokenInf: IFerload = new IFerload {
    override def getConfig: JSONObject = mockFerloadConfigToken

    override def getDownloadLinks(token: String, manifestContent: String): Map[String, String] = ???
  }

  val mockFerloadUnkownMethodInf: IFerload = new IFerload {
    override def getConfig: JSONObject = new JSONObject().put("method", "foo")

    override def getDownloadLinks(token: String, manifestContent: String): Map[String, String] = ???
  }

  before {
    mockUserConfig.clear()
  }

  test("config has been updated (method = password)") {
    new CommandLine(new Configure(mockUserConfig, appTestConfig, mockCommandLineInf, mockFerloadInf)).execute()
    assert(mockUserConfig.get(FerloadUrl).equals("http://ferload"))
    assert(mockUserConfig.get(Method).equals("password"))
    assert(mockUserConfig.get(Username).equals("foo"))
    assert(mockUserConfig.get(Token) == null)
    assert(mockUserConfig.get(KeycloakUrl).equals("http://keycloak"))
    assert(mockUserConfig.get(KeycloakRealm).equals("abc"))
    assert(mockUserConfig.get(KeycloakClientId).equals("123"))
    assert(mockUserConfig.get(KeycloakAudience).equals("456"))
  }

  test("config has been updated (method = token)") {
    new CommandLine(new Configure(mockUserConfig, appTestConfig, mockCommandLineTokenInf, mockFerloadTokenInf)).execute()
    assert(mockUserConfig.get(FerloadUrl).equals("http://ferload"))
    assert(mockUserConfig.get(Method).equals("token"))
    assert(mockUserConfig.get(Token) == "aaa.bbb.ccc")
  }

  test("unknown auth method") {
    val cmd = new CommandLine(new Configure(mockUserConfig, appTestConfig, mockCommandLineTokenInf, mockFerloadUnkownMethodInf))
    assertCommandException(cmd, "Unknown authentication method: foo")
  }

  test("existing config has been updated") {
    mockUserConfig.set(Username, "foo")
    new CommandLine(new Configure(mockUserConfig, appTestConfig, mockCommandLineInf, mockFerloadInf)).execute()
    assert(mockUserConfig.get(Username).equals("foo (existing)"))
  }

  test("reset") {
    mockUserConfig.set(Username, "foo")
    new CommandLine(new Configure(mockUserConfig, appTestConfig, mockCommandLineInf, mockFerloadInf)).execute("-r")
    assert(mockUserConfig.get(Username).equals("foo"))
  }
  
  test("revoke user token") {
    mockUserConfig.set(Username, "foo")
    mockUserConfig.set(Token, "token")
    new CommandLine(new Configure(mockUserConfig, appTestConfig, mockCommandLineInf, mockFerloadInf)).execute("-ubar")
    assert(mockUserConfig.get(Username).equals("bar"))
    assert(mockUserConfig.get(Token) == null)
  }

}
