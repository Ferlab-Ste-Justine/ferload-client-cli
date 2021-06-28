package ca.ferlab.ferload.client.clients.inf

import java.io.File

trait IFerload {
  def getDownloadLink(token: String, manifest: File): String
}
