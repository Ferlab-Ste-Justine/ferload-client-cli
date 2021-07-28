package ca.ferlab.ferload.client.commands

import ca.ferlab.ferload.client.clients.inf.{ICommandLine, IFerload}
import ca.ferlab.ferload.client.commands.factory.{BaseCommand, CommandBlock}
import ca.ferlab.ferload.client.configurations._
import com.typesafe.config.Config
import org.apache.commons.lang3.StringUtils
import org.json.JSONObject
import picocli.CommandLine.{Command, Option}

import java.util.Optional

@Command(name = "configure", mixinStandardHelpOptions = true, description = Array(" Help configure this tool."), version = Array("0.1"))
class Configure(userConfig: UserConfig, appConfig: Config, commandLine: ICommandLine, ferload: IFerload) extends BaseCommand(appConfig) with Runnable {

  @Option(names = Array("-f", "--ferload-url"), description = Array("Ferload url"))
  var ferloadUrl: Optional[String] = Optional.empty

  @Option(names = Array("-u", "--username"), description = Array("username"))
  var username: Optional[String] = Optional.empty

  @Option(names = Array("-p", "--password"), description = Array("password"))
  var password: Optional[String] = Optional.empty

  @Option(names = Array("-r", "--reset"), description = Array("Reset configuration (default: ${DEFAULT-VALUE})"))
  var reset: Boolean = false

  override def run(): Unit = {

    printIntroduction()

    if (reset) {
      new CommandBlock[Unit]("Reset configuration", successEmoji) {
        override def run(): Unit = {
          userConfig.clear()
        }
      }.execute()
    }

    val currentFerloadUrl = userConfig.get(FerloadUrl)
    val currentUsername = userConfig.get(Username)
    val currentPassword = userConfig.get(Password)

    // at least one config already exists
    if (!StringUtils.isAllEmpty(currentFerloadUrl, currentUsername, currentPassword)) {
      println("Press 'enter' to keep the existing configuration [current].")
      println()
    }

    userConfig.set(FerloadUrl, ferloadUrl.orElseGet(() => readLine("-f", currentFerloadUrl)))
    userConfig.set(Username, username.orElseGet(() => readLine("-u", currentUsername)))
    userConfig.set(Password, password.orElseGet(() => readLine("-p", currentPassword, password = true)))
    println()

    val ferloadConfig: JSONObject = new CommandBlock[JSONObject]("Retrieve Ferload configuration", successEmoji) {
      override def run(): JSONObject = {
        ferload.getConfig
      }
    }.execute().getJSONObject("keycloak")

    userConfig.set(KeycloakUrl, ferloadConfig.getString("url"))
    userConfig.set(KeycloakRealm, ferloadConfig.getString("realm"))
    userConfig.set(KeycloakClientId, ferloadConfig.getString("client-id"))

    new CommandBlock[Unit]("Configuration has been successfully updated", successEmoji) {
      override def run(): Unit = {
        userConfig.save()
      }
    }.execute()

  }

  private def readLine(optionName: String, currentValue: String, password: Boolean = false): String = {
    val optionDesc = scala.Option(spec).map(_.optionsMap.get(optionName).description.mkString).getOrElse(optionName)
    val fmt = formatFmt(optionDesc, currentValue, password)
    val line: String = if (password) commandLine.readPassword(fmt) else commandLine.readLine(fmt)
    scala.Option(line).filter(StringUtils.isNotBlank).getOrElse(currentValue)
  }

  private def formatFmt(optionDesc: String, currentValue: String, password: Boolean) = {
    val padding = appConfig.getInt("padding")
    val abbreviate = appConfig.getInt("abbreviate")
    val value = if (password) "[hidden]" else if (StringUtils.isBlank(currentValue)) "" else s"[${StringUtils.abbreviate(currentValue, abbreviate)}]"
    StringUtils.rightPad(optionDesc, padding - value.length) + s" $value"
  }
}