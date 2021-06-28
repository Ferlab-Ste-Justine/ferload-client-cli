package ca.ferlab.ferload.client.clients.inf

import org.json.JSONObject

import java.io.File

trait IFerload {
  def getConfig: JSONObject

  def getDownloadLink(token: String, manifest: File): String
}
