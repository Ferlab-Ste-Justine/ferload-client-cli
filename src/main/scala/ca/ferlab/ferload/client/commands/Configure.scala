package ca.ferlab.ferload.client.commands

import ca.ferlab.ferload.client.clients.KeycloakClient
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
    val currentMethod = userConfig.get(Method)

    // at least one config already exists
    if (!StringUtils.isAllEmpty(currentFerloadUrl, currentMethod)) {
      println("Press 'enter' to keep the existing configuration [current].")
      println()
    }

    userConfig.set(FerloadUrl, ferloadUrl.orElseGet(() => readLine("-f", currentFerloadUrl)))
    println()

    val config: JSONObject = CommandBlock("Retrieve Ferload configuration", successEmoji) {
      ferload.getConfig
    }

    val method: String = CommandBlock("Extract authentication method", successEmoji) {
      userConfig.remove(Token) // always reset token
      if(config.has("method")) config.getString("method") else KeycloakClient.AUTH_METHOD_PASSWORD // fallback to password method if missing
    }

    if (KeycloakClient.AUTH_METHOD_PASSWORD == method) {
      val ferloadConfig = config.getJSONObject("keycloak")
      userConfig.set(KeycloakUrl, ferloadConfig.getString("url"))
      userConfig.set(KeycloakRealm, ferloadConfig.getString("realm"))
      userConfig.set(KeycloakClientId, ferloadConfig.getString("client-id"))
      userConfig.set(KeycloakAudience, ferloadConfig.getString("audience"))

      userConfig.set(Username, username.orElseGet(() => readLine("-u", userConfig.get(Username))))

    } else if (KeycloakClient.AUTH_METHOD_TOKEN == method) {
      val tokenConfig = config.getJSONObject("tokenConfig")
      userConfig.set(TokenLink, tokenConfig.getString("link"))
      userConfig.set(TokenHelper, tokenConfig.getString("helper"))

      println(userConfig.get(TokenHelper) + " " + userConfig.get(TokenLink))
      userConfig.set(Token, readLine("Paste token here", null))

    } else {
      throw new IllegalStateException("Unknown authentication method: " + method)
    }

    println()
    userConfig.set(Method, method)

    CommandBlock("Configuration has been successfully updated", successEmoji) {
      userConfig.save()
    }


  }
}