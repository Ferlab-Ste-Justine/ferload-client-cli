package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.IReportApi
import ca.ferlab.ferload.client.configurations._
import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpGet
import org.apache.http.entity.ContentType

import java.net.URI
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.CollectionHasAsScala

class ReportApiClient(userConfig: UserConfig) extends BaseHttpClient with IReportApi {
  lazy val url: String = userConfig.get(ReportApiManifestUrl)


  override def getManifestById(manifestId: String, token: String): List[String] = {
    val requestUri = new URI(s"$url/$manifestId")
    val httpRequest = new HttpGet(requestUri)
    httpRequest.addHeader(HttpHeaders.AUTHORIZATION, s"Bearer $token")
    httpRequest.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType)
    val (body, status) = executeHttpRequest(httpRequest)

    status match {
      case s if s < 300  => body.get.lines().collect(Collectors.toList[String]).asScala.toList
      case _ => throw new IllegalStateException(formatExceptionMessage(s"Failed to retrieve manifest for id: $manifestId", status, body))
    }
  }

}