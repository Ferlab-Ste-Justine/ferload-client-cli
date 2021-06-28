package ca.ferlab.ferload.client.configurations

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.Properties

class UserConfig(val path: String) {

  val file: File = new File(path)
  val props: Properties = new Properties

  reload()

  def get(configName: UserConfigName): String = {
    props.getProperty(configName.name, null)
  }

  def set(configName: UserConfigName, value: String): Unit = {
    Option(value).map(props.put(configName.name, _))
  }

  def clear(): Unit = {
    props.clear()
    save()
  }

  def reload(): Unit = {
    val fis = new FileInputStream(file)
    props.load(fis)
    fis.close()
  }

  def save(): Unit = {
    val fos = new FileOutputStream(file)
    props.store(fos, "Automatically generated content")
    fos.close()
  }

}
