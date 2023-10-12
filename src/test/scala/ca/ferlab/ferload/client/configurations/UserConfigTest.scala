package ca.ferlab.ferload.client.configurations

import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

import java.io.{File, FileInputStream}
import java.util.Properties

class UserConfigTest extends AnyFunSuite with BeforeAndAfter {

  val path: String = File.createTempFile(".ferload-client", ".properties").getAbsolutePath
  val userConfig = new UserConfig(path)

  before {
    userConfig.clear()
  }

  test("set / get / remove") {
    userConfig.set(Username, "foo")
    assert(userConfig.get(Username).equals("foo"))
    userConfig.remove(Username)
    assert(userConfig.get(Username) == null)
  }

  test("clear") {
    userConfig.set(Username, "foo")
    assert(userConfig.get(Username).equals("foo"))
    userConfig.clear()
    assert(userConfig.get(Username) == null)
  }

  test("save") {
    assert(getCurrentSavedValue(Username).isEmpty)
    userConfig.set(Username, "foo")
    userConfig.save()
    assert(userConfig.get(Username).equals("foo"))
    assert(getCurrentSavedValue(Username).get.equals("foo"))
  }

  def getCurrentSavedValue(userConfigName: UserConfigName): Option[String] = {
    val props = new Properties()
    val fis = new FileInputStream(path)
    props.load(fis)
    fis.close()
    Option(props.get(userConfigName.name)).map(_.toString)
  }

}
