package ca.ferlab.ferload.client.clients

import scala.util.control.NoStackTrace

class UnauthorizedException(message: String) extends RuntimeException with NoStackTrace {
  override def getMessage: String = message
}
