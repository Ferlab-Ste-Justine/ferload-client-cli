package ca.ferlab.ferload.client.commands

import ca.ferlab.ferload.client.clients.inf.{ICommandLine, IFerload, IKeycloak, IS3}
import ca.ferlab.ferload.client.commands.factory.{BaseCommand, CommandBlock}
import ca.ferlab.ferload.client.configurations._
import com.typesafe.config.Config
import org.apache.commons.lang3.StringUtils
import picocli.CommandLine
import picocli.CommandLine.{Command, Option}

import java.io.File
import scala.io.Source

@Command(name = "download", mixinStandardHelpOptions = true, description = Array("Download files based on provided manifest."),
  version = Array("0.1"))
class Download(userConfig: UserConfig,
               appConfig: Config,
               commandLine: ICommandLine,
               keycloak: IKeycloak,
               ferload: IFerload,
               s3: IS3) extends BaseCommand(appConfig) with Runnable {

  @Option(names = Array("-m", "--manifest"), description = Array("manifest file location (default: ${DEFAULT-VALUE})"))
  var manifest: File = new File("manifest.tsv")

  @Option(names = Array("-o", "--output-dir"), description = Array("downloads location (default: ${DEFAULT-VALUE})"))
  var outputDir: File = new File(".")

  override def run(): Unit = {

    val ferloadUrl = userConfig.get(FerloadUrl)
    val username = userConfig.get(Username)
    val password = userConfig.get(Password)
    val keycloakUrl = userConfig.get(KeycloakUrl)
    val keycloakRealm = userConfig.get(KeycloakRealm)
    val keycloakClientId = userConfig.get(KeycloakClientId)

    if (StringUtils.isAnyBlank(ferloadUrl, username, password, keycloakUrl, keycloakRealm, keycloakClientId)) {
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

      val manifestFile: File = new CommandBlock[File]("Checking manifest file", successEmoji, padding) {
        override def run(): File = {
          getManifestFile
        }
      }.execute()

      val token: String = new CommandBlock[String]("Retrieve user credentials", successEmoji, padding) {
        override def run(): String = {
          keycloak.getUserCredentials(username, password)
        }
      }.execute()

      val links: Map[String, String] = new CommandBlock[Map[String, String]]("Retrieve Ferload download link(s)", successEmoji, padding) {
        override def run(): Map[String, String] = {
          ferload.getDownloadLinks(token, manifestFile)
        }
      }.execute()

      println("Download(s) in progress ...")
      println()
      val files = s3.download(outputDir, links)
      println()
      println()

      println(s"Total downloaded files: ${files.size} located here: ${outputDir.getAbsolutePath}")
      println()
    }
  }

  private def getManifestFile: File = {
    val manifestHeader = appConfig.getString("manifest-header")
    scala.Option(manifest)
      .filter(_.exists())
      .orElse(throw new IllegalStateException("Can't found manifest file at location: " + manifest.getAbsolutePath))
      .filter(f => {
        val source = Source.fromFile(f) // first line is the TSV header, check if valid
        try source.getLines().next().trim.equals(manifestHeader) finally source.close()
      }).orElse(throw new IllegalStateException(s"Invalid manifest file, can't find column: $manifestHeader"))
      .get
  }

}