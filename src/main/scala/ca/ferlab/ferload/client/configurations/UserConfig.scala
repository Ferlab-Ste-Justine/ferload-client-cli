package ca.ferlab.ferload.client.configurations

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.Properties

class UserConfig(val path: String) {

  val file: File = new File(path)
  val props: Properties = new Properties

  reload()

  def get(configName: UserConfigName, defaultValue: String = null): String = {
    props.getProperty(configName.name, defaultValue)
  }

  def set(configName: UserConfigName, value: String): Unit = {
    Option(value).map(props.put(configName.name, _))
  }
  
  def remove(configName: UserConfigName): Unit = {
    props.remove(configName.name)
  }

  def clear(): Unit = {
    props.clear()
    save()
  }

  def save(): Unit = {
    val fos = new FileOutputStream(file)
    props.store(fos, "Automatically generated content")
    fos.close()
  }

  def reload(): Unit = {
    if (!file.exists() && !file.createNewFile()) {
      throw new IllegalStateException("Failed to create configuration file: " + file.getAbsolutePath)
    }
    val fis = new FileInputStream(file)
    props.load(fis)
    fis.close()
  }

}
