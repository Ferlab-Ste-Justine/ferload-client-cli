package ca.ferlab.ferload.client.configurations

sealed abstract class UserConfigName(val name: String)

case object FerloadUrl extends UserConfigName("ferload-url")

case object Username extends UserConfigName("username")

case object Token extends UserConfigName("token")

case object KeycloakUrl extends UserConfigName("keycloak-url")

case object KeycloakRealm extends UserConfigName("keycloak-realm")

case object KeycloakClientId extends UserConfigName("keycloak-client-id")

case object KeycloakAudience extends UserConfigName("keycloak-audience")

case object Method extends UserConfigName("method")

case object TokenRealm extends UserConfigName("token-realm")

case object TokenClientId extends UserConfigName("token-client-id")

case object TokenLink extends UserConfigName("token-link")

case object TokenHelper extends UserConfigName("token-helper")