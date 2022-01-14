package ca.ferlab.ferload.client.configurations

sealed abstract class UserConfigName(val name: String)

case object FerloadUrl extends UserConfigName("ferload-url")

case object Username extends UserConfigName("username")

case object Token extends UserConfigName("token")

case object KeycloakUrl extends UserConfigName("keycloak-url")

case object KeycloakRealm extends UserConfigName("keycloak-realm")

case object KeycloakClientId extends UserConfigName("keycloak-client-id")

case object KeycloakAudience extends UserConfigName("keycloak-audience")