package ca.ferlab.ferload.client.configurations

sealed abstract class UserConfigName(val name: String)

case object ClientId extends UserConfigName("clientId")

case object SecretKey extends UserConfigName("secretKey")

case object Username extends UserConfigName("username")

case object Password extends UserConfigName("password")
