package ca.ferlab.ferload.client.clients.inf

trait IReportApi {
  def getManifestById(manifestId: String, token: String): List[String]
}
