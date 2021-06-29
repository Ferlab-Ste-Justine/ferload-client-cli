package ca.ferlab.ferload.client

import ca.ferlab.ferload.client.clients.{ConsoleCommandLine, FerloadClient, KeycloakClient, S3Client}
import ca.ferlab.ferload.client.commands.factory.CommandFactory
import ca.ferlab.ferload.client.commands.{Configure, Download}
import ca.ferlab.ferload.client.configurations.UserConfig
import com.typesafe.config.{Config, ConfigFactory}
import picocli.CommandLine
import picocli.CommandLine.Command

import java.io.File

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
  def main(args: Array[String]): Unit = {
    if (System.console() == null) {
      println("This program needs to be run inside a command line environment.")
    } else {

      val userHome: String = System.getProperty("user.home")
      val baseConfig = ConfigFactory.load
      val appConfig: Config = baseConfig.getObject("ferload-client").toConfig
      val userConfig: UserConfig = new UserConfig(s"$userHome${File.separator}${appConfig.getString("config-name")}")

      // used to customize command instances creation passing required dependencies
      val commandFactory = new CommandFactory(userConfig, appConfig,
        new ConsoleCommandLine,
        new KeycloakClient(userConfig),
        new FerloadClient(userConfig),
        new S3Client)

      val commandLine = new CommandLine(new Main, commandFactory)
      if (args.nonEmpty) {
        System.exit(commandLine.execute(args: _*))
      } else { // display usage if no sub-command
        commandLine.usage(System.out)
      }
    }
  }
}
