package ca.ferlab.ferload.client.commands

import ca.ferlab.ferload.client.clients.inf.ICommandLine
import ca.ferlab.ferload.client.commands.factory.{BaseCommand, CommandBlock}
import ca.ferlab.ferload.client.configurations._
import com.typesafe.config.Config
import org.apache.commons.lang3.StringUtils
import picocli.CommandLine.{Command, Option}

import java.util.Optional

@Command(name = "configure", mixinStandardHelpOptions = true, description = Array(" Help configure this tools."), version = Array("0.1"))
class Configure(userConfig: UserConfig, appConfig: Config, commandLine: ICommandLine) extends BaseCommand(appConfig) with Runnable {

  @Option(names = Array("-c", "--client-id"), description = Array("Keycloak client-id"))
  var clientId: Optional[String] = Optional.empty // picocli is compatible with Optional (JAVA) not Option (Scala)

  @Option(names = Array("-s", "--secret-key"), description = Array("Keycloak secret-key"))
  var secretKey: Optional[String] = Optional.empty

  @Option(names = Array("-u", "--username"), description = Array("Ferload username"))
  var username: Optional[String] = Optional.empty

  @Option(names = Array("-p", "--password"), description = Array("Ferload password"))
  var password: Optional[String] = Optional.empty

  @Option(names = Array("-r", "--reset"), description = Array("Reset configuration (default: ${DEFAULT-VALUE})"))
  var reset: Boolean = false

  override def run(): Unit = {

    printIntroduction

    if (reset) {
      new CommandBlock[Unit]("Reset configuration", successEmoji) {
        override def run(): Unit = {
          userConfig.clear()
        }
      }.execute()
    }

    val currentClientId = userConfig.get(ClientId)
    val currentSecretKey = userConfig.get(SecretKey)
    val currentUsername = userConfig.get(Username)
    val currentPassword = userConfig.get(Password)

    if (!StringUtils.isAllEmpty(currentClientId, currentSecretKey, currentUsername, currentPassword)) {
      println("Press 'enter' to keep the existing configuration [current].")
      println()
    }

    userConfig.set(ClientId, clientId.orElseGet(() => readLine("-c", currentClientId)))
    userConfig.set(SecretKey, secretKey.orElseGet(() => readLine("-s", currentSecretKey)))
    userConfig.set(Username, username.orElseGet(() => readLine("-u", currentUsername)))
    userConfig.set(Password, password.orElseGet(() => readLine("-p", currentPassword, password = true)))
    println()

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