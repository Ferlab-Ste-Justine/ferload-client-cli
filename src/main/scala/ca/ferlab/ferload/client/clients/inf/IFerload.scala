package ca.ferlab.ferload.client.clients.inf

import org.json.JSONObject

import java.io.File

trait IFerload {
  def getConfig: JSONObject

  def getDownloadLinks(token: String, manifest: File): Map[String, String]
}
