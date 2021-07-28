package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.IKeycloak
import ca.ferlab.ferload.client.configurations.{KeycloakClientId, KeycloakRealm, KeycloakUrl, UserConfig}
import org.keycloak.authorization.client.{AuthzClient, Configuration}

import java.util.Collections

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
    authzClient.obtainAccessToken(username, password).getToken
  }

}