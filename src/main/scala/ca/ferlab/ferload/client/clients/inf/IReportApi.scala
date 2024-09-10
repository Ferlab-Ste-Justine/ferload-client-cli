package ca.ferlab.ferload.client.clients.inf

import ca.ferlab.ferload.client.clients.Error

trait IReportApi {
  def getManifestContentById(manifestId: String, token: String): Either[Error, List[String]]
  def downloadManifestById(manifestId: String, token: String, path: String): Either[Error, Unit]
}
