package ca.ferlab.ferload.client.commands

import ca.ferlab.ferload.client.clients.inf.{ICommandLine, IFerload, IKeycloak, IS3}
import ca.ferlab.ferload.client.clients.{Error, KeycloakClient, ReportApiClient}
import ca.ferlab.ferload.client.commands.Download.FilesDownloadStyle
import ca.ferlab.ferload.client.commands.factory.{BaseCommand, CommandBlock}
import ca.ferlab.ferload.client.configurations._
import ca.ferlab.ferload.client.{LineContent, ManifestContent, OpsNum}
import com.typesafe.config.Config
import org.apache.commons.csv.{CSVFormat, CSVParser, CSVRecord}
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import picocli.CommandLine
import picocli.CommandLine.{ArgGroup, Command, IExitCodeGenerator, Option, Parameters}

import java.io.{File, FileReader}
import java.util.Optional
import java.util.stream.Collectors.toList
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try, Using}

@Command(name = "download", mixinStandardHelpOptions = true, description = Array("Download files based on provided manifest."))
class Download(userConfig: UserConfig,
               appConfig: Config,
               commandLine: ICommandLine,
               keycloak: IKeycloak,
               ferload: IFerload,
               s3: IS3) extends BaseCommand(appConfig, commandLine) with Runnable with IExitCodeGenerator {


  @ArgGroup(exclusive = true, multiplicity = "1") val filesDownloadStyle: FilesDownloadStyle = null

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

      implicit lazy val padding: Int = appConfig.getInt("padding")

      val authMethod = userConfig.get(Method)
      implicit var token: String = userConfig.get(Token)
      val refreshToken = userConfig.get(RefreshToken)

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
        if(keycloak.isValidToken(token)) {
          CommandBlock("Re-use user device credentials", successEmoji, padding) {
            ""
          }
        } else {
          val (newToken, newRefreshToken) = if(!keycloak.isValidToken(refreshToken)){
            val resp = CommandBlock("Retrieve device token", successEmoji, padding) {
              keycloak.getDevice
            }

            CommandBlock("Copy/Paste this URL in your browser and login please: ", successEmoji, padding) {
              println(resp.getString("verification_uri_complete"))
            }
            keycloak.getUserDeviceToken(resp.getString("device_code"), resp.getInt("expires_in"))
          } else {
            CommandBlock("Refresh device credentials", successEmoji, padding) {
              ""
            }
            keycloak.getRefreshedTokens(refreshToken)(userConfig.get(KeycloakRealm), userConfig.get(KeycloakAudience))
          }

          userConfig.set(Token, newToken)
          userConfig.set(RefreshToken, newRefreshToken)
          userConfig.save()
          token = newToken
        }

      }

      //<manifestOnly> option only allowed in case of manifest_id command (-i).
      (if (filesDownloadStyle.byManifestId.manifestOnly) {
        downloadManifest(token, padding)
      } else {
        downloadFiles(token, padding)
      }) match {
        case Right(_) => //nothing
        case Left(e) => throw new IllegalStateException(e.message)
      }
    }
  }

  /**
   * Download the file manifest in TSV format from required ID at desired path
   * @param token JWT token of the user
   * @param padding Padding required for display
   * @return `Right` unit if successful or `Left` Exception if failure
   * */
  private def downloadManifest(token: String, padding: Int) = {
    CommandBlock("Retrieving Manifest from ID", successEmoji, padding) {
      fetchManifestFromId(manifestId = filesDownloadStyle.byManifestId.id.get(), token)
    }
  }

  private def downloadFiles(token: String, padding: Int) = {
    for {
      manifestContent <- extractManifest(token: String, padding: Int)
      links <- {
        val downloadLinks = ferload.getDownloadLinks(token, manifestContent)
        if (downloadLinks.isLeft) {
          // always refresh token if failed
          userConfig.remove(Token)
          userConfig.save()
        }
        downloadLinks
      }
      resp <- downloadFilesWithChecks(manifestContent,links, padding)
    } yield resp
  }

  def downloadFilesWithChecks(manifestContent: ManifestContent, links: Map[LineContent, String], padding: Int): Either[Error, Unit] = {
    val totalExpectedDownloadSize = CommandBlock("Compute total average expected download size", successEmoji, padding) {
      manifestContent.totalSize.getOrElse(
        Try(s3.getTotalExpectedDownloadSize(links.map{ case(k, v) => k.filePointer -> v }, appConfig.getLong("size-estimation-timeout"))) match {
          case Success(size) => size
          case Failure(e) =>
            println()
            println()
            println(s"Failed to compute total average expected download size, reason: ${e.getMessage}")
            print(s"You can still proceed with the download, verify you have remaining disk-space available.")
            NO_SIZE
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
        Left(Error(s"Not enough disk space available $usableSpace < $totalExpectedDownloadSize"))
      } else {
        val files = s3.download(outputDir, links)
        println()
        println()

        println(s"Total downloaded files: ${files.size} located here: ${outputDir.getAbsolutePath}")
        println()
        Right()
      }
    } else {
      Left(Error(s"Aborted by user"))
    }
  }


  /**
   * parse the input string into numeric size
  @param input Input size string. Will accept size of the form: [123, 123 B, 123 KB, 123 MB, 123 GB, 123 TB]
               if not units provided, it assumed to be bytes
   */
  private def extractSize (input: String): Long = {
    if(input.isNumeric) {
      input.toLong
    } else {
      val regex = "^([0-9.]+)\\s([A-Z]{1,2})$".r
      val matched = regex.findAllIn(input)
      val size = matched.group(1)
      matched.group(2) match {
        case "B" => size.toLong
        case "KB" => size.toDouble.toLong * 1024L
        case "MB" => (size.toDouble * math.pow(1024, 2)).toLong
        case "GB" => (size.toDouble * math.pow(1024, 3)).toLong
        case "TB" => (size.toDouble * math.pow(1024, 4)).toLong
        case _ => throw new IllegalStateException(s"Size format not suitable: $input")
      }
    }
  }

  private def extractManifestContentFromEntryList(csvEntryList: List[String]): Either[Error, ManifestContent] = {
    val manifestFilePointer = scala.Option(userConfig.get(ClientManifestFilePointer)).getOrElse(appConfig.getString("manifest-file-pointer"))
    val manifestFileName = scala.Option(userConfig.get(ClientManifestFileName))
    val manifestSize = scala.Option(userConfig.get(ClientManifestFileSize)).getOrElse(appConfig.getString("manifest-size"))
    val manifestSeparator = appConfig.getString("manifest-separator").charAt(0)

    Try {
      val csvFormat = CSVFormat.DEFAULT
        .withDelimiter(manifestSeparator)
        .withIgnoreEmptyLines()
        .withTrim()

      val header = csvEntryList.head.split(manifestSeparator).toList
      val record = csvEntryList.tail

      val csv = record.flatMap(l => CSVParser.parse(l, csvFormat.withHeader(header: _*)).getRecords.asScala.toList)

      val fileIdColumnIndex = header.indexOf(manifestFilePointer)
      val fileDisplayNameColumnIndex = manifestFileName.map(displayCol =>  header.indexOf(displayCol))
      val sizeColumnIndex = header.indexOf(manifestSize)

      if (fileIdColumnIndex == -1) {
        throw new IllegalStateException("Missing column: " + manifestFilePointer)
      }

      val lines = csv.flatMap(record => {
          parseCVSRecord(record)(fileIdColumnIndex, fileDisplayNameColumnIndex.getOrElse(-1), sizeColumnIndex)
        })

      if (lines.isEmpty) {
        throw new IllegalStateException("Empty content")
      }
      val hasEmptyFileSizes = lines.exists(l => l.size.isEmpty)

      val totalSize = if (hasEmptyFileSizes) {
        None
      } else {
        val size = lines.foldLeft(0L) { (acc, l) =>
          l.size.map(s => acc + s).getOrElse(0L)
        }
        Some(size)
      }

      ManifestContent(lines, totalSize)
    } match {
      case Success(value) => Right(value)
      case Failure(e) => Left(Error(s"Invalid manifest file: " + e.getMessage))
    }
  }

  private def extractManifestContentFromFile(manifestFile: File): Either[Error, ManifestContent] = {
    val manifestFilePointer = scala.Option(userConfig.get(ClientManifestFilePointer)).getOrElse(appConfig.getString("manifest-file-pointer"))
    val manifestFileName = scala.Option(userConfig.get(ClientManifestFileName))
    val manifestSize = scala.Option(userConfig.get(ClientManifestFileSize)).getOrElse(appConfig.getString("manifest-size"))
    val manifestSeparator = appConfig.getString("manifest-separator").charAt(0)


    Using(new FileReader(manifestFile)) { reader =>
      val parser = CSVFormat.DEFAULT
        .withDelimiter(manifestSeparator)
        .withIgnoreEmptyLines()
        .withTrim()
        .withFirstRecordAsHeader()
        .parse(reader)
      val fileIdColumnIndex = parser.getHeaderMap.getOrDefault(manifestFilePointer, -1)
      val fileDisplayNameColumnIndex = manifestFileName.map(displayCol => parser.getHeaderMap.getOrDefault(displayCol, -1)).map(_.toInt)
      val sizeColumnIndex = parser.getHeaderMap.getOrDefault(manifestSize, -1)

      if (fileIdColumnIndex == -1) {
        throw new IllegalStateException("Missing column: " + manifestFilePointer)
      }

      val lines = parser.getRecords.stream().map(record => {
          parseCVSRecord(record)(fileIdColumnIndex, fileDisplayNameColumnIndex.getOrElse(-1), sizeColumnIndex)
        })
        .collect(toList[scala.Option[LineContent]])
        .asScala.toSeq.flatten

      if (lines.isEmpty) {
        throw new IllegalStateException("Empty content")
      }
      val hasEmptyFileSizes = lines.exists(l => l.size.isEmpty)

      val totalSize = if (hasEmptyFileSizes) {
        None
      } else {
        val size = lines.foldLeft(0L) { (acc, l) =>
          l.size.map(s => acc + s).getOrElse(0L)
        }
        Some(size)
      }

      ManifestContent(lines, totalSize)

    } match {
      case Success(value) => Right(value)
      case Failure(e) => Left(Error(s"Invalid manifest file: " + e.getMessage))
    }
  }


  private def extractManifest(token: String, padding: Int): Either[Error, ManifestContent] = {
    if (filesDownloadStyle.byManifestId.id.isPresent) {
      CommandBlock("Extracting Manifest from ID", successEmoji, padding) {
        fetchAndExtractManifestContentFromId(manifestId = filesDownloadStyle.byManifestId.id.get(), token)
      }
    } else {
      CommandBlock("Checking manifest file", successEmoji, padding) {
        if (filesDownloadStyle.byManifest.isPresent && !filesDownloadStyle.byManifest.get().exists()) {
          Left(Error("Manifest file not found at location: " + filesDownloadStyle.byManifest.get().getAbsolutePath))
        } else {
          extractManifestContentFromFile(filesDownloadStyle.byManifest.get())
        }
      }
    }
  }

  private def fetchAndExtractManifestContentFromId(manifestId: String, token: String): Either[Error, ManifestContent] = {
    val reportApiClient = new ReportApiClient(userConfig)

    val manifestEntryList = reportApiClient.getManifestContentById(manifestId, token)

    manifestEntryList.flatMap(extractManifestContentFromEntryList)
  }

  private def fetchManifestFromId(manifestId: String, token: String): Either[Error, Unit] = {
    val reportApiClient = new ReportApiClient(userConfig)

    reportApiClient.downloadManifestById(manifestId, token, outputDir.getAbsolutePath)
  }

  private def parseCVSRecord (record: CSVRecord)
                             (filePointerColIndex: Int, displayColIndex: Int, sizeColIndex: Int): scala.Option[LineContent] = {
    val pointer =  record.get(filePointerColIndex)
    val display =  if(displayColIndex >= 0) Some(record.get(displayColIndex)) else None
    val size = if(sizeColIndex >= 0) Some(record.get(sizeColIndex)) else None

    if (StringUtils.isNotBlank(pointer)) {
      Some(LineContent(pointer, display,  size.map(extractSize)))
    } else None
  }
  


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

object Download {
  //Must supply manifest path OR manifestId
  class FilesDownloadStyle {
    @Option(names = Array("-m", "--manifest"), required = true, description = Array("manifest file location"))
    var byManifest: Optional[File] = Optional.empty[File]
    @ArgGroup(exclusive = false, multiplicity = "1")
    val byManifestId: ManifestId = new ManifestId()
  }

  class ManifestId {
    @Option(names = Array("-i", "--manifest-id"), required = true, description = Array("manifest ID"))
    var id: Optional[String] = Optional.empty[String]
    @Option(names = Array("--manifest-only"), required = false, description = Array("Download the manifest only from the manifest id"))
    var manifestOnly: Boolean = false
  }
}
