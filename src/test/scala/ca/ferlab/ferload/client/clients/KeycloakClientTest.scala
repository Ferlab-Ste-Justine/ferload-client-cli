package ca.ferlab.ferload.client.clients

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.scalatest.funsuite.AnyFunSuite

import java.time.{LocalDateTime, ZoneId}
import java.util.Date

class KeycloakClientTest extends AnyFunSuite {
  
  val client = new KeycloakClient(null)
  val algorithm: Algorithm = Algorithm.HMAC256("secret");

  test("isTokenValid") {
    assert(!client.isValidToken(null))
    assert(!client.isValidToken(""))
    assert(!client.isValidToken("a.b.c"))
    val expiredDate = Date.from(LocalDateTime.now().minusMinutes(5).atZone(ZoneId.systemDefault()).toInstant())
    val expiredToken = JWT.create()
      .withExpiresAt(expiredDate)
      .sign(algorithm);
    assert(!client.isValidToken(expiredToken))
    val validDate = Date.from(LocalDateTime.now().plusMinutes(5).atZone(ZoneId.systemDefault()).toInstant())
    val validToken = JWT.create()
      .withExpiresAt(validDate)
      .sign(algorithm);
    assert(client.isValidToken(validToken))
  }

}
