package ca.ferlab.ferload.client.commands

import ca.ferlab.ferload.client.clients.KeycloakClient
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
import scala.collection.mutable
import scala.util.{Failure, Success, Try, Using}

@Command(name = "download", mixinStandardHelpOptions = true, description = Array("Download files based on provided manifest."))
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

  @Option(names = Array("-p", "--password"), description = Array("password"), hidden = true)
  var password: Optional[String] = Optional.empty
  
  private val NO_SIZE = 0L;

  override def run(): Unit = {

    if (!isValidConfiguration(userConfig)) {
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

      val manifestContent: ManifestContent = CommandBlock("Checking manifest file", successEmoji, padding) {
        extractManifestContent
      }

      val authMethod = userConfig.get(Method)
      var token = userConfig.get(Token)
      if (KeycloakClient.AUTH_METHOD_PASSWORD == authMethod) {
        if (!keycloak.isValidToken(token)) {
          val passwordStr = password.orElseGet(() => readLine("-p", "", password = true))
          println()
          token = CommandBlock("Retrieve user credentials", successEmoji, padding) {
            val newToken = keycloak.getUserCredentials(userConfig.get(Username), passwordStr, null)
            userConfig.set(Token, newToken)
            userConfig.save()
            newToken
          }
        } else {
          CommandBlock("Re-use user credentials", successEmoji, padding) {
            ""
          }
        }
      } else if (KeycloakClient.AUTH_METHOD_TOKEN == authMethod) {
        token = CommandBlock("Retrieve user credentials", successEmoji, padding) {
          keycloak.getUserCredentials(null, null, userConfig.get(Token))
        }
      } else if (KeycloakClient.AUTH_DEVICE == authMethod) {
        val resp = CommandBlock("Retrieve device token", successEmoji, padding) {
          keycloak.getDevice
        }

        token = CommandBlock("Copy/Paste this URL in your browser and login please: ", successEmoji, padding) {
          println(resp.getString("verification_uri_complete"))
          val newToken = keycloak.getUserDeviceToken(resp.getString("device_code"), resp.getInt("expires_in"))
          userConfig.set(Token, newToken)
          userConfig.save()
          newToken
        }
      }

      val links: Map[String, String] = CommandBlock("Retrieve Ferload download link(s)", successEmoji, padding) {
        Try(ferload.getDownloadLinks(token, manifestContent.urls)) match {
          case Success(links) => links
          case Failure(e) => {
            // always refresh token if failed
            userConfig.remove(Token)
            userConfig.save()
            throw e
          }
        }
      }

      val totalExpectedDownloadSize = CommandBlock("Compute total average expected download size", successEmoji, padding) {
        manifestContent.totalSize.getOrElse(
          Try(s3.getTotalExpectedDownloadSize(links, appConfig.getLong("size-estimation-timeout"))) match {
            case Success(size) => size
            case Failure(e) => {
              println()
              println()
              println(s"Failed to compute total average expected download size, reason: ${e.getMessage}")
              print(s"You can still proceed with the download, verify you have remaining disk-space available.")
              NO_SIZE
            }
          })
      }
      
      val totalExpectedDownloadSizeStr = if(totalExpectedDownloadSize > 0) FileUtils.byteCountToDisplaySize(totalExpectedDownloadSize) else "-"
      val usableSpace = s3.getTotalAvailableDiskSpaceAt(outputDir)
      val usableSPaceStr = FileUtils.byteCountToDisplaySize(usableSpace)
      
      val downloadAgreement = appConfig.getString("download-agreement")
      val agreedToDownload = commandLine.readLine(s"The total average expected download size will be " +
        s"$totalExpectedDownloadSizeStr do you want to continue (your available disk space is: $usableSPaceStr) ? [$downloadAgreement]")
      println()

      if (downloadAgreement.startsWith(agreedToDownload) || StringUtils.isBlank(agreedToDownload)) {

        if (usableSpace < totalExpectedDownloadSize) {
          throw new IllegalStateException(s"Not enough disk space available $usableSpace < $totalExpectedDownloadSize")
        }

        val files = s3.download(outputDir, links)
        println()
        println()

        println(s"Total downloaded files: ${files.size} located here: ${outputDir.getAbsolutePath}")
        println()
      } else {
        throw new IllegalStateException(s"Aborted by user")
      }
    }
  }

  private def extractManifestContent: ManifestContent = {
    val manifestHeader = appConfig.getString("manifest-header")
    val manifestSize = appConfig.getString("manifest-size")
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
      val urls = new mutable.StringBuilder
      var totalSize = NO_SIZE
      val fileIdColumnIndex = parser.getHeaderMap.getOrDefault(manifestHeader, -1)
      val sizeColumnIndex = parser.getHeaderMap.getOrDefault(manifestSize, -1)

      if (fileIdColumnIndex == -1) {
        throw new IllegalStateException("Missing column: " + manifestHeader)
      }

      parser.getRecords.stream().forEach(record => {
        val url = record.get(fileIdColumnIndex)
        if (StringUtils.isNotBlank(url)) {
          urls.append(s"$url\n")
        }
        if (record.isSet(sizeColumnIndex)) {  // size column exist (optional)
          val size = record.get(sizeColumnIndex)
          if (StringUtils.isNotBlank(size)) {
            totalSize += size.toLong
          }
        }
      })

      if (urls.isEmpty) {
        throw new IllegalStateException("Empty content")
      }


      ManifestContent(urls.toString(), if(totalSize == NO_SIZE) None else Some(totalSize))

    } match {
      case Success(value) => value
      case Failure(e) => throw new IllegalStateException(s"Invalid manifest file: " + e.getMessage, e)
    }
  }
  
  case class ManifestContent(urls: String, totalSize: scala.Option[Long])

  override def getExitCode: Int = 1

  private def isValidConfiguration(userConfig: UserConfig): Boolean = {
    val ferloadUrl = userConfig.get(FerloadUrl)
    val method = userConfig.get(Method)
    if (KeycloakClient.AUTH_METHOD_PASSWORD == method) {
      val username = userConfig.get(Username)
      val keycloakUrl = userConfig.get(KeycloakUrl)
      val keycloakRealm = userConfig.get(KeycloakRealm)
      val keycloakClientId = userConfig.get(KeycloakClientId)
      val keycloakAudience = userConfig.get(KeycloakAudience)
      StringUtils.isNoneBlank(ferloadUrl, username, keycloakUrl, keycloakRealm, keycloakClientId, keycloakAudience)
    } else if (KeycloakClient.AUTH_METHOD_TOKEN == method) {
      val token = userConfig.get(Token)
      val clientId = userConfig.get(TokenClientId)
      val realm = userConfig.get(TokenRealm)
      StringUtils.isNoneBlank(ferloadUrl, token, clientId, realm)
    } else if (KeycloakClient.AUTH_DEVICE == method) {
      val keycloakUrl = userConfig.get(KeycloakUrl)
      val keycloakRealm = userConfig.get(KeycloakRealm)
      val keycloakAudience = userConfig.get(KeycloakAudience)
      StringUtils.isNoneBlank(ferloadUrl, keycloakUrl, keycloakRealm, keycloakAudience)
    } else {
      false; // method is null ?
    }
  }
}