package ca.ferlab.ferload.client.commands.factory

import ca.ferlab.ferload.client.Main
import ca.ferlab.ferload.client.commands.{Configure, Download}
import org.scalatest.funsuite.AnyFunSuite

class CommandFactoryTest extends AnyFunSuite {

  val factory = new CommandFactory(null, null, null, null, null)

  test("configure") {
    val cmd = factory.create(classOf[Configure])
    assert(cmd != null)
    assert(cmd.isInstanceOf[Configure])
  }

  test("download") {
    val cmd = factory.create(classOf[Download])
    assert(cmd != null)
    assert(cmd.isInstanceOf[Download])
  }

  test("main") {
    val cmd = factory.create(classOf[Main])
    assert(cmd != null)
    assert(cmd.isInstanceOf[Main])
  }
}
