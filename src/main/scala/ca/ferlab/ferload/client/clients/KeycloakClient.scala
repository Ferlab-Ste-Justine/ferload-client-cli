package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.IKeycloak
import ca.ferlab.ferload.client.configurations._
import com.auth0.jwt.JWT
import org.apache.commons.lang3.StringUtils
import org.keycloak.authorization.client.{AuthzClient, Configuration}
import org.keycloak.representations.idm.authorization.AuthorizationRequest

import java.time.Instant
import java.util.{Collections, Date}

class KeycloakClient(config: UserConfig) extends IKeycloak {

  // lazy because will fail to init if configuration is empty
  // let's instantiate the client only when needed
  lazy val keycloakAuthClientConfig = new Configuration(
    config.get(KeycloakUrl),
    config.get(KeycloakRealm),
    config.get(KeycloakClientId),
    Collections.singletonMap("secret", ""), // secret has to be defined even if empty
    null) // keycloak API will create a default httpClient

  lazy val authzClient: AuthzClient = AuthzClient.create(keycloakAuthClientConfig)

  override def getUserCredentials(username: String, password: String): String = {
    val token = authzClient.obtainAccessToken(username, password).getToken
    val authRequest: AuthorizationRequest = new AuthorizationRequest()
    authRequest.setAudience(config.get(KeycloakAudience))
    authzClient.authorization(token).authorize(authRequest).getToken
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