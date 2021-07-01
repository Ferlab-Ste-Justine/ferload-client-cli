package ca.ferlab.ferload.client.commands.factory

import ca.ferlab.ferload.client.clients.inf.{ICommandLine, IFerload, IKeycloak, IS3}
import ca.ferlab.ferload.client.commands.{Configure, Download}
import ca.ferlab.ferload.client.configurations.UserConfig
import com.typesafe.config.Config
import picocli.CommandLine
import picocli.CommandLine.IFactory

class CommandFactory(userConfig: UserConfig,
                     appConfig: Config,
                     commandLineInf: ICommandLine,
                     keycloakInf: IKeycloak,
                     ferloadInf: IFerload,
                     s3Inf: IS3) extends IFactory {

  // required for PicoCLI reflection tools GraalVM
  def this() = this(null, null, null, null, null, null)

  override def create[K](clazz: Class[K]): K = {
    try {
      if (isClassCommand(clazz, classOf[Configure])) {
        new Configure(userConfig, appConfig, commandLineInf, ferloadInf).asInstanceOf[K]
      } else if (isClassCommand(clazz, classOf[Download])) {
        new Download(userConfig, appConfig, commandLineInf, keycloakInf, ferloadInf, s3Inf).asInstanceOf[K]
      } else {
        throw new IllegalStateException(s"Unknown command: ${clazz.getName}")
      }
    } catch {
      case _: Throwable => CommandLine.defaultFactory.create(clazz); // fallback if missing
    }
  }

  private def isClassCommand[K, T <: BaseCommand](c1: Class[K], c2: Class[T]): Boolean = {
    c1.getName.equals(c2.getName)
  }
}
