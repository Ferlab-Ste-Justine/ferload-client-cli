package ca.ferlab.ferload.client.clients.inf

import org.json.JSONObject

trait IFerload {
  def getConfig: JSONObject

  def getDownloadLinks(token: String, manifestContent: String): Map[String, String]
}
