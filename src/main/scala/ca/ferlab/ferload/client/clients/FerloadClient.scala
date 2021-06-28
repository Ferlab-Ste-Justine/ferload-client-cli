package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.IFerload
import com.typesafe.config.Config

import java.io.File

class FerloadClient(config: Config) extends IFerload {
  override def getDownloadLink(token: String, manifest: File): String = "download_link"
}
