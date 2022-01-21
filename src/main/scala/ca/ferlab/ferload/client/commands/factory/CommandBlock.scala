package ca.ferlab.ferload.client.commands.factory

import org.apache.commons.lang3.StringUtils
object CommandBlock {
  def apply[T](description: String, success: String, padding: Int = 4)(f: => T):T = {
    // rightPad allows padding with min size of 4 because of the '...' ellipsis
    print(s"${StringUtils.rightPad(description, padding)}")
    val t = f
    println(s" $success")
    println()
    t


  }
}

