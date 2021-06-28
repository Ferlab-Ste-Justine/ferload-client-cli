package ca.ferlab.ferload.client.commands.factory

import com.typesafe.config.Config
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec

class BaseCommand(appConfig: Config) {
  @Spec
  protected val spec: CommandSpec = null

  protected val successEmoji = new String(Character.toChars(0x2705))

  def printIntroduction(): Unit = {
    println("Welcome to Ferload Client, this tools will download the files based\n" +
      "on the provided manifest. For any questions or feedbacks please contact:\n" +
      appConfig.getString("contact") + "\n")
  }

}
