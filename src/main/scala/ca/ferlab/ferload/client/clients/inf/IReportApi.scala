package ca.ferlab.ferload.client.clients.inf

trait IReportApi {
  def getManifestContentById(manifestId: String, token: String): List[String]
  def downloadManifestById(manifestId: String, token: String, path: String): Unit
}
