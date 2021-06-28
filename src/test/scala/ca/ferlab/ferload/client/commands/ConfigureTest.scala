package ca.ferlab.ferload.client.commands

import ca.ferlab.ferload.client.clients.inf.ICommandLine
import ca.ferlab.ferload.client.configurations._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import picocli.CommandLine

class ConfigureTest extends AnyFunSuite with BeforeAndAfter {

  val appTestConfig: Config = ConfigFactory.load.getObject("ferload-client").toConfig
  val path: String = "/tmp/.ferload-client.properties"
  val mockUserConfig = new UserConfig(path)
  val mockCommandLineInf: ICommandLine = new ICommandLine {
    override def readLine(fmt: String): String = {
      val mock = fmt.trim match {
        case "Keycloak client-id" => "123"
        case "Keycloak client-id [123]" => "123"
        case "Keycloak secret-key" => "abc"
        case "Ferload username" => "foo"
        case _ => fail(s"$fmt isn't mocked")
      }
      mock
    }

    override def readPassword(fmt: String): String = {
      val mock = fmt.trim match {
        case "Ferload password [hidden]" => "bar"
        case _ => fail(s"$fmt isn't mocked")
      }
      mock
    }
  }

  before {
    mockUserConfig.clear()
  }

  test("config has been updated") {
    new CommandLine(new Configure(mockUserConfig, appTestConfig, mockCommandLineInf)).execute()
    assert(mockUserConfig.get(ClientId).equals("123"))
    assert(mockUserConfig.get(SecretKey).equals("abc"))
    assert(mockUserConfig.get(Username).equals("foo"))
    assert(mockUserConfig.get(Password).equals("bar"))
  }

  test("existing config has been updated") {
    mockUserConfig.set(ClientId, "123")
    new CommandLine(new Configure(mockUserConfig, appTestConfig, mockCommandLineInf)).execute()
    assert(mockUserConfig.get(ClientId).equals("123"))
    assert(mockUserConfig.get(SecretKey).equals("abc"))
    assert(mockUserConfig.get(Username).equals("foo"))
    assert(mockUserConfig.get(Password).equals("bar"))
  }

}
