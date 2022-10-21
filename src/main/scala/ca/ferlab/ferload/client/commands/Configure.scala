package ca.ferlab.ferload.client.commands

import ca.ferlab.ferload.client.clients.inf.{ICommandLine, IFerload}
import ca.ferlab.ferload.client.commands.factory.{BaseCommand, CommandBlock}
import ca.ferlab.ferload.client.configurations._
import com.typesafe.config.Config
import org.apache.commons.lang3.StringUtils
import org.json.JSONObject
import picocli.CommandLine.{Command, Option}

import java.util.Optional

@Command(name = "configure", mixinStandardHelpOptions = true, description = Array(" Help configure this tool."))
class Configure(userConfig: UserConfig, appConfig: Config, commandLine: ICommandLine, ferload: IFerload) extends BaseCommand(appConfig, commandLine) with Runnable {

  @Option(names = Array("-f", "--ferload-url"), description = Array("Ferload url"))
  var ferloadUrl: Optional[String] = Optional.empty

  @Option(names = Array("-u", "--username"), description = Array("username"))
  var username: Optional[String] = Optional.empty

  @Option(names = Array("-r", "--reset"), description = Array("Reset configuration (default: ${DEFAULT-VALUE})"))
  var reset: Boolean = false

  override def run(): Unit = {

    printIntroduction()

    if (reset) {
      CommandBlock("Reset configuration", successEmoji) {
        userConfig.clear()
      }

    }

    val currentFerloadUrl = userConfig.get(FerloadUrl)
    val currentUsername = userConfig.get(Username)

    // at least one config already exists
    if (!StringUtils.isAllEmpty(currentFerloadUrl, currentUsername)) {
      println("Press 'enter' to keep the existing configuration [current].")
      println()
    }

    userConfig.set(FerloadUrl, ferloadUrl.orElseGet(() => readLine("-f", currentFerloadUrl)))
    userConfig.set(Username, username.orElseGet(() => readLine("-u", currentUsername)))
    // if username changed then revoke last token
    if (!userConfig.get(Username).equals(currentUsername)) userConfig.remove(Token)
    println()

    val ferloadConfig: JSONObject = CommandBlock("Retrieve Ferload configuration", successEmoji) {
      ferload.getConfig
    }.getJSONObject("keycloak")

    userConfig.set(KeycloakUrl, ferloadConfig.getString("url"))
    userConfig.set(KeycloakRealm, ferloadConfig.getString("realm"))
    userConfig.set(KeycloakClientId, ferloadConfig.getString("client-id"))
    userConfig.set(KeycloakAudience, ferloadConfig.getString("audience"))

    CommandBlock("Configuration has been successfully updated", successEmoji) {
      userConfig.save()
    }


  }
}