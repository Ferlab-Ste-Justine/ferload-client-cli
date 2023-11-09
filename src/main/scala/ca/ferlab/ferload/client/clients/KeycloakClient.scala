package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.IKeycloak
import ca.ferlab.ferload.client.configurations._
import com.auth0.jwt.JWT
import org.apache.commons.lang3.StringUtils
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.message.BasicNameValuePair
import org.json.JSONObject

import java.time.Instant
import java.util.Date

object KeycloakClient {
  val AUTH_METHOD_PASSWORD = "password"
  val AUTH_METHOD_TOKEN = "token"
}

class KeycloakClient(config: UserConfig) extends BaseHttpClient with IKeycloak {

  override def getUserCredentials(username: String, password: String, refreshToken: String): String = {
    if (StringUtils.isNoneBlank(username, password)) {
      getRPT(getAccessToken(username, password))
    } else if(StringUtils.isNotBlank(refreshToken)) {
      getRefreshedToken(refreshToken)
    } else {
      throw new IllegalStateException("No valid user credentials")
    }
  }

  private def getAccessToken(username: String, password: String) = {
    val request = new HttpPost(s"${config.get(KeycloakUrl)}/realms/${config.get(KeycloakRealm)}/protocol/openid-connect/token")

    val form = new java.util.ArrayList[BasicNameValuePair]()
    form.add(new BasicNameValuePair("client_id", config.get(KeycloakClientId)))
    form.add(new BasicNameValuePair("grant_type", "password"))
    form.add(new BasicNameValuePair("username", username))
    form.add(new BasicNameValuePair("password", password))

    request.setEntity(new UrlEncodedFormEntity(form, charset))

    execute(request)
  }

  private def getRefreshedToken(refreshToken: String) = {
    val request = new HttpPost(s"${config.get(KeycloakUrl)}/realms/${config.get(TokenRealm)}/protocol/openid-connect/token")

    val form = new java.util.ArrayList[BasicNameValuePair]()
    form.add(new BasicNameValuePair("client_id", config.get(TokenClientId)))
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

    execute(request)
  }

  private def execute(request: HttpPost) = {
    val (body, status) = executeHttpRequest(request)
    val token = status match {
      case 200 => body.map(new JSONObject(_)).get.getString("access_token")
      case _ => throw new IllegalStateException(formatExceptionMessage("Failed to get access token", status, body))
    }
    token
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