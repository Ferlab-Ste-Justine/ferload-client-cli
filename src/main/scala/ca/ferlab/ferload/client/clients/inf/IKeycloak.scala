package ca.ferlab.ferload.client.clients.inf

trait IKeycloak {
  def getUserCredentials(username: String, password: String, refreshToken:String): String

  def isValidToken(token: String): Boolean
}
