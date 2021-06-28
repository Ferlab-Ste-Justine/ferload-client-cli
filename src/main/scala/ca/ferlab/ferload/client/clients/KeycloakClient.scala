package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.IKeycloak
import ca.ferlab.ferload.client.configurations.{ClientId, SecretKey, UserConfig}
import com.typesafe.config.Config
import org.keycloak.authorization.client.{AuthzClient, Configuration}

import java.util.Collections

class KeycloakClient(userConfig: UserConfig, keycloakConfig: Config) extends IKeycloak {
  override def getUserCredentials(username: String, password: String): String = {
    val keycloakAuthClientConfig = new Configuration(
      keycloakConfig.getString("auth-server-url"),
      keycloakConfig.getString("realm"),
      userConfig.get(ClientId),
      Collections.singletonMap("secret", userConfig.get(SecretKey)),
      null) // keycloak API will create a default httpClient
    val authzClient = AuthzClient.create(keycloakAuthClientConfig)
    val response = authzClient.obtainAccessToken(username, password)
    response.getToken
  }
}