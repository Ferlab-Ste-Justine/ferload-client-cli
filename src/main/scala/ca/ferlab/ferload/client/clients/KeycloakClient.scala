package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.IKeycloak
import ca.ferlab.ferload.client.configurations.{KeycloakClientId, KeycloakRealm, KeycloakUrl, UserConfig}
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.message.BasicNameValuePair
import org.json.JSONObject

class KeycloakClient(config: UserConfig) extends HttpClient with IKeycloak {

  lazy val url: String = config.get(KeycloakUrl)
  lazy val realm: String = config.get(KeycloakRealm)
  lazy val clientId: String = config.get(KeycloakClientId)

  override def getUserCredentials(username: String, password: String): String = {
    val request = new HttpPost(s"$url/realms/$realm/protocol/openid-connect/token")

    val form = new java.util.ArrayList[BasicNameValuePair]()
    form.add(new BasicNameValuePair("client_id", clientId))
    form.add(new BasicNameValuePair("grant_type", "password"))
    form.add(new BasicNameValuePair("username", username))
    form.add(new BasicNameValuePair("password", password))
    request.setEntity(new UrlEncodedFormEntity(form, charset))
    val (body, status) = executeHttpRequest(request)
    val token = status match {
      case 200 => body.map(new JSONObject(_)).get.getString("access_token")
      case _ => throw new IllegalStateException(formatExceptionMessage("Failed to retrieve download link(s)", status, body))
    }
    token
  }

}