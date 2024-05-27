package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.IKeycloak
import ca.ferlab.ferload.client.configurations._
import cats.effect.IO
import com.auth0.jwt.JWT
import org.apache.commons.lang3.StringUtils
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.message.BasicNameValuePair
import org.json.JSONObject
import retry.RetryDetails.GivingUp
import retry.{RetryDetails, RetryPolicies, RetryPolicy, retryingOnFailures}

import java.lang.Math.round
import java.time.Instant
import java.util.Date
import scala.concurrent.duration.DurationInt

object KeycloakClient {
  val AUTH_METHOD_PASSWORD = "password"
  val AUTH_METHOD_TOKEN = "token"
  val AUTH_DEVICE = "device"
}

class KeycloakClient(config: UserConfig) extends BaseHttpClient with IKeycloak {
  private val MAX_TOKEN_EXPIRE = 600


  override def getUserCredentials(username: String, password: String, refreshToken: String): String = {
    if (StringUtils.isNoneBlank(username, password)) {
      getRPT(getAccessToken(username, password))
    } else if(StringUtils.isNotBlank(refreshToken)) {
      getRefreshedToken(refreshToken)(realm = config.get(TokenRealm), client = config.get(TokenClientId))
    } else {
      throw new IllegalStateException("No valid user credentials")
    }
  }

  override def getDevice: JSONObject = {
    val request = new HttpPost(s"${config.get(KeycloakUrl)}/realms/${config.get(KeycloakRealm)}/protocol/openid-connect/auth/device")
    request.addHeader("Content-Type", "application/x-www-form-urlencoded")

    val form = new java.util.ArrayList[BasicNameValuePair]()
    form.add(new BasicNameValuePair("client_id", config.get(KeycloakAudience)))

    request.setEntity(new UrlEncodedFormEntity(form, charset))

    executeDeviceFetch(request)
  }

  override def getUserDeviceToken(deviceCode: String, expiresIn: Int = MAX_TOKEN_EXPIRE): (String, String) = {
    val request = new HttpPost(s"${config.get(KeycloakUrl)}/realms/${config.get(KeycloakRealm)}/protocol/openid-connect/token")
    request.addHeader("Content-Type", "application/x-www-form-urlencoded")

    val form = new java.util.ArrayList[BasicNameValuePair]()
    form.add(new BasicNameValuePair("client_id", config.get(KeycloakAudience)))
    form.add(new BasicNameValuePair("device_code", deviceCode))
    form.add(new BasicNameValuePair("grant_type", "urn:ietf:params:oauth:grant-type:device_code"))

    request.setEntity(new UrlEncodedFormEntity(form, charset))

    executeWithRetry(request, expiresIn)
  }

  override def getRefreshedTokens(refreshToken: String)(realm: String, client: String): (String, String) = {
    val resp =  refreshCredentials(refreshToken: String)(realm: String, client: String)

    (resp.getString("access_token"), resp.getString("refresh_token"))
  }

  private def getAccessToken(username: String, password: String) = {
    val request = new HttpPost(s"${config.get(KeycloakUrl)}/realms/${config.get(KeycloakRealm)}/protocol/openid-connect/token")

    val form = new java.util.ArrayList[BasicNameValuePair]()
    form.add(new BasicNameValuePair("client_id", config.get(KeycloakClientId)))
    form.add(new BasicNameValuePair("grant_type", "password"))
    form.add(new BasicNameValuePair("username", username))
    form.add(new BasicNameValuePair("password", password))

    request.setEntity(new UrlEncodedFormEntity(form, charset))

    execute(request).getString("access_token")
  }

  private def getRefreshedToken(refreshToken: String)(realm: String, client: String) = {
    val refreshedCreds =  refreshCredentials(refreshToken: String)(realm: String, client: String)

    refreshedCreds.getString("access_token")
  }

  private def refreshCredentials(refreshToken: String)(realm: String, client: String) = {
    val request = new HttpPost(s"${config.get(KeycloakUrl)}/realms/$realm/protocol/openid-connect/token")

    val form = new java.util.ArrayList[BasicNameValuePair]()
    form.add(new BasicNameValuePair("client_id", client))
    form.add(new BasicNameValuePair("grant_type", "refresh_token"))
    form.add(new BasicNameValuePair("refresh_token", refreshToken))

    request.setEntity(new UrlEncodedFormEntity(form, charset))

    execute(request)
  }

  private def getRPT(accessToken: String) = {
    val request = new HttpPost(s"${config.get(KeycloakUrl)}/realms/${config.get(KeycloakRealm)}/protocol/openid-connect/token")
    request.addHeader("Authorization", "Bearer " + accessToken)

    val form = new java.util.ArrayList[BasicNameValuePair]()
    form.add(new BasicNameValuePair("audience", config.get(KeycloakAudience)))
    form.add(new BasicNameValuePair("grant_type", "urn:ietf:params:oauth:grant-type:uma-ticket"))

    request.setEntity(new UrlEncodedFormEntity(form, charset))

    execute(request).getString("access_token")
  }

  private def execute(request: HttpPost): JSONObject = {
    val (body, status) = executeHttpRequest(request)
    val parsedBody = status match {
      case 200 => body.map(new JSONObject(_)).get
      case _ => throw new IllegalStateException(formatExceptionMessage("Failed to get access token", status, body))
    }
    parsedBody
  }

  private def executeNoVerify(request: HttpPost) = {
    val (body, status) = executeHttpRequest(request)
    (status, body)
  }

  private val policyDelay: RetryPolicy[IO] = RetryPolicies.constantDelay[IO](5.second)
  private def policyMaxRetries(expireLimit: Int): RetryPolicy[IO] = RetryPolicies.limitRetries(round(expireLimit / 5))

  private def onFailure(resp: (Int, Option[String]), retryDetails: RetryDetails) = {
    val (_, body) = resp
    val loggingPending = body
      .map(new JSONObject(_).getString("error"))
      .map(Seq("slow_down", "authorization_pending").contains)

    (retryDetails, loggingPending) match {
      case (_: RetryDetails.WillDelayAndRetry, Some(true)) => IO() //nothing, try fetch again
      case (_: GivingUp, _) => IO(throw new IllegalStateException("Token has expired, please restart"))
      case _ => IO(throw new IllegalStateException(s"Failed to get access token: ${body.getOrElse("")}"))
    }
  }

  private def isResultOk(resp: (Int, Option[String])): IO[Boolean] = IO {
    val (status, _) = resp
    status.equals(200)
  }

  private def executeWithRetry(request: HttpPost, expiresIn: Int) = {
    import cats.effect.unsafe.implicits.global
    retryingOnFailures(policyDelay.join(policyMaxRetries(expiresIn)), isResultOk, onFailure)(IO(executeNoVerify(request)))
      .map { case(_, body) =>
        val parsedBody = body.map(new JSONObject(_)).get
        (parsedBody.getString("access_token"), parsedBody.getString("refresh_token"))
      }.unsafeRunSync()
  }

  private def executeDeviceFetch(request: HttpPost): JSONObject = {
    val (body, status) = executeHttpRequest(request)
    status match {
      case 200 => body.map(new JSONObject(_)).get
      case _ => throw new IllegalStateException(formatExceptionMessage("Failed to get access token", status, body))
    }
  }

  override def isValidToken(token: String): Boolean = {
    var isValid = true;
    try {
      if (StringUtils.isBlank(token)) {
        isValid = false
      } else {
        val date = Date.from(Instant.now.minusSeconds(15))  // give us a few delay
        val decoded = JWT.decode(token)
        if(decoded.getExpiresAt.before(date)) {
          isValid = false
        }
      }
    } catch {
      case _: Exception => isValid = false
    }
    isValid
  }

}