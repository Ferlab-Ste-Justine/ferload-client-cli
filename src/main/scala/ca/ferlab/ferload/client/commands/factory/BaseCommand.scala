package ca.ferlab.ferload.client.commands.factory

import ca.ferlab.ferload.client.clients.inf.ICommandLine
import com.typesafe.config.Config
import org.apache.commons.lang3.StringUtils
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec

class BaseCommand(appConfig: Config, commandLine: ICommandLine) {
  @Spec
  protected val spec: CommandSpec = null

  protected val successEmoji = new String(Character.toChars(0x2705))

  def printIntroduction(): Unit = {
    println(
      s"""Welcome to Ferload Client, this tools will download
the files based on the provided manifest.""")
    println()
  }

  protected def readLine(optionName: String, currentValue: String, password: Boolean = false): String = {
    val optionDesc = scala.Option(spec).map(_.optionsMap.get(optionName).description.mkString).getOrElse(optionName)
    val fmt = formatFmt(optionDesc, currentValue, password)
    val line: String = if (password) commandLine.readPassword(fmt) else commandLine.readLine(fmt)
    scala.Option(line).filter(StringUtils.isNotBlank).getOrElse(currentValue)
  }

  protected def formatFmt(optionDesc: String, currentValue: String, password: Boolean): String = {
    val padding = appConfig.getInt("padding")
    val abbreviate = appConfig.getInt("abbreviate")
    val value = if (password) "[hidden]" else if (StringUtils.isBlank(currentValue)) "" else s"[${StringUtils.abbreviate(currentValue, abbreviate)}]"
    StringUtils.rightPad(optionDesc, padding - value.length) + s" $value"
  }

}
