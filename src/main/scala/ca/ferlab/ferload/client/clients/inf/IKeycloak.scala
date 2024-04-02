package ca.ferlab.ferload.client.clients.inf

import org.json.JSONObject

trait IKeycloak {
  def getUserCredentials(username: String, password: String, refreshToken:String): String

  def getDevice: JSONObject

  def getUserDeviceToken(deviceCode: String, expiresIn: Int): String

  def isValidToken(token: String): Boolean
}
