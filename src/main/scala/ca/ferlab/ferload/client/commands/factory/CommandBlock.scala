package ca.ferlab.ferload.client.commands.factory

import org.apache.commons.lang3.StringUtils

abstract class CommandBlock[T](description: String, success: String, padding: Int = 4) {

  def run(): T

  def execute(): T = {
    print(s"${StringUtils.rightPad(description, padding)}")
    val t = run()
    println(s" $success")
    println()
    t
  }

}

