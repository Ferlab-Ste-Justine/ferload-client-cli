package ca.ferlab.ferload.client.configurations

sealed abstract class UserConfigName(val name: String)

case object FerloadUrl extends UserConfigName("ferload-url")

case object Username extends UserConfigName("username")

case object Token extends UserConfigName("token")

case object RefreshToken extends UserConfigName("refresh_token")

case object KeycloakUrl extends UserConfigName("keycloak-url")

case object KeycloakRealm extends UserConfigName("keycloak-realm")

case object KeycloakClientId extends UserConfigName("keycloak-client-id")

case object KeycloakAudience extends UserConfigName("keycloak-audience")

case object Method extends UserConfigName("method")

case object TokenRealm extends UserConfigName("token-realm")

case object TokenClientId extends UserConfigName("token-client-id")

case object TokenLink extends UserConfigName("token-link")

case object TokenHelper extends UserConfigName("token-helper")

case object ClientManifestFilePointer extends UserConfigName("client-config-manifest-file-pointer")

case object ClientManifestFileName extends UserConfigName("client-config-manifest-filename")

case object ClientManifestFileSize extends UserConfigName("client-config-manifest-size")

case object ReportApiManifestUrl extends UserConfigName("client-config-manifest-url")