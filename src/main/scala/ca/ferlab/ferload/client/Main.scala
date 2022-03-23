package ca.ferlab.ferload.client

import ca.ferlab.ferload.client.clients.{ConsoleCommandLine, FerloadClient, KeycloakClient, S3Client}
import ca.ferlab.ferload.client.commands.factory.CommandFactory
import ca.ferlab.ferload.client.commands.{Configure, Download}
import ca.ferlab.ferload.client.configurations.UserConfig
import com.amazonaws.SDKGlobalConfiguration
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.FileUtils
import picocli.CommandLine
import picocli.CommandLine.Command

import java.io.File
import scala.util.{Failure, Success, Using}

@Command(name = "ferload-client", mixinStandardHelpOptions = true,
  version = Array("0.1"),
  description = Array("Official Ferload Client command line interface for files download."),
  subcommands = Array(classOf[Configure], classOf[Download]))
class Main extends Runnable {
  override def run(): Unit = {
    // nothing to do
  }
}

object Main {
  
  Using(this.getClass.getClassLoader.getResourceAsStream("cacerts.jks")) { is =>
    val tempFile = File.createTempFile("cacerts", ".jks")
    FileUtils.copyInputStreamToFile(is, tempFile)
    //System.setProperty(SDKGlobalConfiguration.DISABLE_CERT_CHECKING_SYSTEM_PROPERTY, "true")
    System.setProperty("javax.net.ssl.trustStore", tempFile.getPath)  // keycloak / ferload / aws
    System.setProperty("javax.net.ssl.trustStorePassword", "changeit")
    System.setProperty("javax.net.ssl.trustStoreType", "jks")
    tempFile.getPath
  } match {
    case Success(_) =>
    case Failure(e) => throw new IllegalStateException(s"Failed to load certificates: " + e.getMessage, e)
  }
  
  def main(args: Array[String]): Unit = {
    val userHome: String = System.getProperty("user.home")
    val baseConfig = ConfigFactory.load
    val appConfig: Config = baseConfig.getObject("ferload-client").toConfig
    val userConfig: UserConfig = new UserConfig(s"$userHome${File.separator}${appConfig.getString("config-name")}")

    // used to customize command instances creation passing required dependencies
    val commandFactory = new CommandFactory(userConfig, appConfig,
      new ConsoleCommandLine,
      new KeycloakClient(userConfig),
      new FerloadClient(userConfig),
      new S3Client(appConfig.getInt("download-files-pool")))

    val commandLine = new CommandLine(new Main, commandFactory)
    if (args.nonEmpty) {
      System.exit(commandLine.execute(args: _*))
    } else { // display usage if no sub-command
      commandLine.usage(System.out)
    }
  }
}
