package ca.ferlab.ferload.client.clients.inf

import org.json.JSONObject

import java.io.File

trait IFerload {
  def getConfig: JSONObject

  def getLinks(token: String, manifest: File): Array[String]
}
