package ca.ferlab.ferload.client.clients.inf

import ca.ferlab.ferload.client.{LineContent, ManifestContent}
import org.json.JSONObject

trait IFerload {
  def getConfig: JSONObject

  def getDownloadLinks(token: String, manifestContent: ManifestContent): Map[LineContent, String]
}
