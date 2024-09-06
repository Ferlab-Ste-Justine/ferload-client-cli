package ca.ferlab.ferload.client.commands

import ca.ferlab.ferload.client.{LineContent, ManifestContent}
import ca.ferlab.ferload.client.clients.inf.{ICommandLine, IFerload, IKeycloak, IS3}
import ca.ferlab.ferload.client.configurations._
import com.typesafe.config.{Config, ConfigFactory}
import org.json.JSONObject
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import picocli.CommandLine

import java.io.File
import java.util

class DownloadTest extends AnyFunSuite with BeforeAndAfter {

  val appTestConfig: Config = ConfigFactory.load.getObject("ferload-client").toConfig
  val path: String = File.createTempFile(".ferload-client", ".properties").getAbsolutePath
  val mockUserConfig = new UserConfig(path)
  val manifestEmptyFile: String = this.getClass.getClassLoader.getResource("manifest-empty.tsv").getFile
  val manifestValidFile: String = this.getClass.getClassLoader.getResource("manifest-valid.tsv").getFile
  val manifestInvalidFile: String = this.getClass.getClassLoader.getResource("manifest-invalid.tsv").getFile
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
  val mockCommandLineInfAgreed: ICommandLine = new ICommandLine {
    override def readLine(fmt: String): String = "yes"

    override def readPassword(fmt: String): String = "bar"
  }

  val mockCommandLineInfAgreedSimple: ICommandLine = new ICommandLine {
    override def readLine(fmt: String): String = "y"

    override def readPassword(fmt: String): String = "bar"
  }

  val mockCommandLineInfNotAgreed: ICommandLine = new ICommandLine {
    override def readLine(fmt: String): String = "no"

    override def readPassword(fmt: String): String = "bar"
  }

  val mockKeycloakInf: IKeycloak = new IKeycloak {
    override def getUserCredentials(username: String, password: String, refreshToken: String): String = {
      assert(username.equals("foo"))
      assert(password.equals("bar"))
      "token"
    }

    override def isValidToken(token: String): Boolean = false

    override def getDevice: JSONObject = ???

    override def getUserDeviceToken(deviceCode: String, expiresIn: Int): (String, String) = ???

    override def getRefreshedTokens(refreshToken: String)(realm: String, client: String): (String, String) = ???
  }

  val mockKeycloakValidTokenInf: IKeycloak = new IKeycloak {
    override def getUserCredentials(username: String, password: String, refreshToken: String): String = ???

    override def isValidToken(token: String): Boolean = true

    override def getDevice: JSONObject = ???

    override def getUserDeviceToken(deviceCode: String, expiresIn: Int): (String, String) = ???

    override def getRefreshedTokens(refreshToken: String)(realm: String, client: String): (String, String) = ???
  }

  val mockFerload: IFerload = new IFerload {
    override def getDownloadLinks(token: String, manifestContent: ManifestContent): Map[LineContent, String] = {
      assert(token.equals("token"))
      Map(LineContent(filePointer = "f1") -> "link1", LineContent(filePointer = "f2") -> "link2", LineContent(filePointer = "f3") -> "link3")
    }

    override def getConfig: JSONObject = mockFerloadConfigPassword
  }

  before {
    mockUserConfig.clear()
    mockUserConfig.set(FerloadUrl, "foo")
    mockUserConfig.set(Method, "password")
    mockUserConfig.set(Username, "foo")
    mockUserConfig.set(Token, "token")
    mockUserConfig.set(KeycloakUrl, "foo")
    mockUserConfig.set(KeycloakRealm, "foo")
    mockUserConfig.set(KeycloakClientId, "foo")
    mockUserConfig.set(KeycloakAudience, "foo")
  }

  test("run configure first") {
    mockUserConfig.clear()
    new Download(mockUserConfig, appTestConfig, mockCommandLineInf, null, mockFerload, null).run()
    assert(mockUserConfig.get(Username).equals("foo")) // at least one value is set
  }

  test("manifest not found") {
    val cmd = new CommandLine(new Download(mockUserConfig, appTestConfig, mockCommandLineInfAgreed, mockKeycloakInf, null, null))
    assertCommandException(cmd, "Manifest file not found at location:", "-m", "/mount/some.tsv")
  }

  test("invalid output-dir") {
    val cmd = new CommandLine(new Download(mockUserConfig, appTestConfig, null, null, null, null))
    assertCommandException(cmd, "Failed to access the output directory", "-m", "/mount/some.tsv", "-o", "/mount")
  }

  test("manifest empty") {
    val cmd = new CommandLine(new Download(mockUserConfig, appTestConfig, mockCommandLineInfAgreed, mockKeycloakInf, null, null))
    assertCommandException(cmd, "Empty content", "-m", manifestEmptyFile)
  }

  test("manifest invalid") {
    val cmd = new CommandLine(new Download(mockUserConfig, appTestConfig, mockCommandLineInfAgreed, mockKeycloakInf, null, null))
    assertCommandException(cmd, "Missing column", "-m", manifestInvalidFile)
  }

  test("call keycloak / ferload") {

    val mockS3: IS3 = new IS3 {
      override def download(outputDir: File, links: Map[LineContent, String]): Set[File] = {
        assert(links.size == 2)
        Set(new File("f1"), new File("f2"))
      }

      override def getTotalExpectedDownloadSize(links: Map[String, String], timeout: Long): Long = 0L

      override def getTotalAvailableDiskSpaceAt(manifest: File): Long = 1L
    }

    val cmd = new CommandLine(new Download(mockUserConfig, appTestConfig, mockCommandLineInfAgreed, mockKeycloakInf, mockFerload, mockS3))
    assert(cmd.execute("-m", manifestValidFile) == 1)
  }

  test("did not agreed to download") {

    val mockS3: IS3 = new IS3 {
      override def download(outputDir: File, links: Map[LineContent, String]): Set[File] = {
        assert(links.size == 2)
        Set(new File("f1"), new File("f2"))
      }

      override def getTotalExpectedDownloadSize(links: Map[String, String], timeout: Long): Long = 0L

      override def getTotalAvailableDiskSpaceAt(manifest: File): Long = 1L
    }

    val cmd = new CommandLine(new Download(mockUserConfig, appTestConfig, mockCommandLineInfNotAgreed, mockKeycloakInf, mockFerload, mockS3))
    assertCommandException(cmd, "Aborted by user", "-m", manifestValidFile)
  }

  test("not enough disk space") {

    val mockS3: IS3 = new IS3 {
      override def download(outputDir: File, links: Map[LineContent, String]): Set[File] = {
        assert(links.size == 2)
        Set(new File("f1"), new File("f2"))
      }

      override def getTotalExpectedDownloadSize(links: Map[String, String], timeout: Long): Long = ???

      override def getTotalAvailableDiskSpaceAt(manifest: File): Long = 1L
    }

    val cmd = new CommandLine(new Download(mockUserConfig, appTestConfig, mockCommandLineInfAgreed, mockKeycloakInf, mockFerload, mockS3))
    assertCommandException(cmd, "Not enough disk space available", "-m", manifestValidFile)
  }

  test("stored token is valid") {

    val mockS3: IS3 = new IS3 {
      override def download(outputDir: File, links: Map[LineContent, String]): Set[File] = {
        assert(links.size == 2)
        Set(new File("f1"), new File("f2"))
      }

      override def getTotalExpectedDownloadSize(links: Map[String, String], timeout: Long): Long = 1L

      override def getTotalAvailableDiskSpaceAt(manifest: File): Long = 3L
    }

    val cmd = new CommandLine(new Download(mockUserConfig, appTestConfig, mockCommandLineInfAgreed, mockKeycloakValidTokenInf, mockFerload, mockS3))
    assert(cmd.execute("-m", manifestValidFile) == 1)
  }

  test("confirm download simplified") {

    val mockS3: IS3 = new IS3 {
      override def download(outputDir: File, links: Map[LineContent, String]): Set[File] = {
        assert(links.size == 3)
        Set(new File("f1"), new File("f2"))
      }

      override def getTotalExpectedDownloadSize(links: Map[String, String], timeout: Long): Long = 1L

      override def getTotalAvailableDiskSpaceAt(manifest: File): Long = 20000000L
    }

    val cmd = new CommandLine(new Download(mockUserConfig, appTestConfig, mockCommandLineInfAgreedSimple, mockKeycloakValidTokenInf, mockFerload, mockS3))
    assert(cmd.execute("-m", manifestValidFile) == 1)
  }

  test("Erroneous download inputs") {

    val mockS3: IS3 = new IS3 {
      override def download(outputDir: File, links: Map[LineContent, String]): Set[File] = {
        assert(links.size == 3)
        Set(new File("f1"), new File("f2"))
      }

      override def getTotalExpectedDownloadSize(links: Map[String, String], timeout: Long): Long = 1L

      override def getTotalAvailableDiskSpaceAt(manifest: File): Long = 20000000L
    }

    val cmd = new CommandLine(new Download(mockUserConfig, appTestConfig, mockCommandLineInfAgreedSimple, mockKeycloakValidTokenInf, mockFerload, mockS3))

    //Picocli Error: Passing a <manifest> and a <manifest_id> in the same command (mutually exclusive)
    assert(cmd.execute("-m", manifestValidFile, "i", "1234") == 2)

    //Picocli Error: Passing a <manifest> and a requesting to download the manifest <--manifest-only> in the same command
    assert(cmd.execute("-m", manifestValidFile, "--manifest-only") == 2)

    //Picocli Error: No argument for download <-i> or <-m>, one is required
    assert(cmd.execute() == 2)
  }

}
