package ca.ferlab.ferload.client.commands

import ca.ferlab.ferload.client.clients.inf.{ICommandLine, IFerload, IKeycloak, IS3}
import ca.ferlab.ferload.client.configurations._
import com.typesafe.config.{Config, ConfigFactory}
import org.json.JSONObject
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import java.io.File

class DownloadTest extends AnyFunSuite with BeforeAndAfter {

  val appTestConfig: Config = ConfigFactory.load.getObject("ferload-client").toConfig
  val path: String = "/tmp/.ferload-client.properties"
  val mockUserConfig = new UserConfig(path)
  val mockCommandLineInf: ICommandLine = new ICommandLine {
    override def readLine(fmt: String): String = {
      val mock = fmt.trim match {
        case "Ferload url" => "http://ferload"
        case "username" => "foo"
        case _ => fail(s"$fmt isn't mocked")
      }
      mock
    }

    override def readPassword(fmt: String): String = {
      val mock = fmt.trim match {
        case "password [hidden]" => "bar"
        case _ => fail(s"$fmt isn't mocked")
      }
      mock
    }
  }

  before {
    mockUserConfig.clear()
  }

  test("run configure first") {
    new Download(mockUserConfig, appTestConfig, mockCommandLineInf, null, null, null).run()
    assert(mockUserConfig.get(Username).equals("foo")) // at least one value is set
  }

  test("call keycloak / ferload") {
    mockUserConfig.set(Username, "foo")
    mockUserConfig.set(Password, "bar")

    val mockKeycloakInf: IKeycloak = new IKeycloak {
      override def getUserCredentials(username: String, password: String): String = {
        assert(username.equals("foo"))
        assert(password.equals("bar"))
        "token"
      }
    }

    val mockFerload: IFerload = new IFerload {
      override def getDownloadLinks(token: String, manifest: File): Map[String, String] = {
        assert(token.equals("token"))
        assert(manifest.getName.equals("manifest.tsv"))
        Map("f1" -> "link1", "f2" -> "link2", "f2" -> "link2")
      }

      override def getConfig: JSONObject = ???
    }

    val mockS3: IS3 = new IS3 {
      override def download(outputDir: File, links: Map[String, String]): Array[File] = {
        Array(new File("f1"), new File("f2"), new File("f3"))
      }
    }

    new Download(mockUserConfig, appTestConfig, null, mockKeycloakInf, mockFerload, mockS3).run()
  }

}
