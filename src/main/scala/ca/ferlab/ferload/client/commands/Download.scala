package ca.ferlab.ferload.client.commands

import ca.ferlab.ferload.client.clients.inf.{ICommandLine, IFerload, IKeycloak, IS3}
import ca.ferlab.ferload.client.commands.factory.{BaseCommand, CommandBlock}
import ca.ferlab.ferload.client.configurations._
import com.typesafe.config.Config
import org.apache.commons.csv.CSVFormat
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import picocli.CommandLine
import picocli.CommandLine.{Command, IExitCodeGenerator, Option}

import java.io.{File, FileReader}
import java.util.Optional
import scala.util.{Failure, Success, Using}

@Command(name = "download", mixinStandardHelpOptions = true, description = Array("Download files based on provided manifest."),
  version = Array("0.1"))
class Download(userConfig: UserConfig,
               appConfig: Config,
               commandLine: ICommandLine,
               keycloak: IKeycloak,
               ferload: IFerload,
               s3: IS3) extends BaseCommand(appConfig, commandLine) with Runnable with IExitCodeGenerator {

  @Option(names = Array("-m", "--manifest"), description = Array("manifest file location (default: ${DEFAULT-VALUE})"))
  var manifest: File = new File("manifest.tsv")

  @Option(names = Array("-o", "--output-dir"), description = Array("downloads location (default: ${DEFAULT-VALUE})"))
  var outputDir: File = new File(".")

  @Option(names = Array("-p", "--password"), description = Array("password"))
  var password: Optional[String] = Optional.empty

  override def run(): Unit = {

    val ferloadUrl = userConfig.get(FerloadUrl)
    val username = userConfig.get(Username)
    var token = userConfig.get(Token)
    val keycloakUrl = userConfig.get(KeycloakUrl)
    val keycloakRealm = userConfig.get(KeycloakRealm)
    val keycloakClientId = userConfig.get(KeycloakClientId)
    val keycloakAudience = userConfig.get(KeycloakAudience)

    if (StringUtils.isAnyBlank(ferloadUrl, username, keycloakUrl, keycloakRealm, keycloakClientId, keycloakAudience)) {
      println("Configuration is missing, please fill the missing information first.")
      println()
      new CommandLine(new Configure(userConfig, appConfig, commandLine, ferload)).execute()
      println("Please retry the last command.") // better let the user re-launch than trying to re-execute the command #infiniteloop
      println()
    } else {

      printIntroduction()

      if (!outputDir.exists() && !outputDir.mkdirs() || !outputDir.canWrite) {
        throw new IllegalStateException("Failed to access the output directory: " + outputDir.getAbsolutePath)
      }

      val padding = appConfig.getInt("padding")

      val manifestContent: String = new CommandBlock[String]("Checking manifest file", successEmoji, padding) {
        override def run(): String = {
          extractManifestContent
        }
      }.execute()
      
      if (!keycloak.isValidToken(token)) {
        val passwordStr = password.orElseGet(() => readLine("-p", "", password = true))
        println()
        token = new CommandBlock[String]("Retrieve user credentials", successEmoji, padding) {
          override def run(): String = {
            val newToken = keycloak.getUserCredentials(username, passwordStr)
            userConfig.set(Token, newToken)
            userConfig.save()
            newToken
          }
        }.execute()
      } else {
        new CommandBlock[String]("Re-use user credentials", successEmoji, padding) {
          override def run(): String = { "" }
        }.execute()
      }
      
      val links: Map[String, String] = new CommandBlock[Map[String, String]]("Retrieve Ferload download link(s)", successEmoji, padding) {
        override def run(): Map[String, String] = {
          ferload.getDownloadLinks(token, manifestContent)
        }
      }.execute()

      val totalExpectedDownloadSize = s3.getTotalExpectedDownloadSize(links)
      val downloadAgreement = appConfig.getString("download-agreement")
      val agreedToDownload = commandLine.readLine(s"The total average expected download size will be " +
        s"${FileUtils.byteCountToDisplaySize(totalExpectedDownloadSize)} do you want to continue ? [$downloadAgreement]")
      println()

      if (agreedToDownload.equals(downloadAgreement) || StringUtils.isBlank(agreedToDownload)) {

        val usableSpace = s3.getTotalAvailableDiskSpaceAt(manifest)
        if (usableSpace < totalExpectedDownloadSize) {
          throw new IllegalStateException(s"Not enough disk space available $usableSpace < $totalExpectedDownloadSize")
        }

        val files = s3.download(outputDir, links)
        println()
        println()

        println(s"Total downloaded files: ${files.size} located here: ${outputDir.getAbsolutePath}")
        println()
      }
    }
  }
  
  private def extractManifestContent: String = {
    val manifestHeader = appConfig.getString("manifest-header")
    val manifestSeparator = appConfig.getString("manifest-separator").charAt(0)

    if (!manifest.exists()) {
      throw new IllegalStateException("Manifest file not found at location: " + manifest.getAbsolutePath)
    }

    Using(new FileReader(manifest)) { reader =>
      val parser = CSVFormat.DEFAULT
        .withDelimiter(manifestSeparator)
        .withIgnoreEmptyLines()
        .withTrim()
        .withFirstRecordAsHeader()
        .parse(reader)
      val builder = new StringBuilder
      val fileIdColumnIndex = parser.getHeaderMap.getOrDefault(manifestHeader, -1)

      if (fileIdColumnIndex == -1) {
        throw new IllegalStateException("Missing column: " + manifestHeader)
      }

      parser.getRecords.stream().forEach(record => {
        builder.append(s"${record.get(fileIdColumnIndex)}\n")
      })

      if (builder.isEmpty) {
        throw new IllegalStateException("Empty content")
      }

      builder.toString()

    } match {
      case Success(value) => value
      case Failure(e) => throw new IllegalStateException(s"Invalid manifest file: " + e.getMessage, e)
    }
  }

  override def getExitCode: Int = 1
}