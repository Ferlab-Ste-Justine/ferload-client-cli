package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.IReportApi
import ca.ferlab.ferload.client.configurations._
import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpGet
import org.apache.http.entity.ContentType

import java.util.stream.Stream
import java.net.URI
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.CollectionHasAsScala

class ReportApiClient(userConfig: UserConfig) extends BaseHttpClient with IReportApi {
  lazy val url: String = userConfig.get(ReportApiManifestUrl)


  override def getManifestContentById(manifestId: String, token: String): Either[Error, List[String]] = {
    val requestUri = new URI(s"$url/$manifestId")
    val httpRequest = new HttpGet(requestUri)
    httpRequest.addHeader(HttpHeaders.AUTHORIZATION, s"Bearer $token")
    httpRequest.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType)
    val (body, status) = executeHttpRequest(httpRequest)

    status match {
      case s if s < 300  =>
        Right(body.get.lines.asInstanceOf[Stream[String]].collect(Collectors.toList[String]).asScala.toList)
      case _ => Left(Error(formatExceptionMessage(s"Failed to retrieve manifest for id: $manifestId", status, body)))
    }
  }


  /**
   * Download the manifest by ID from report api service
   * @param manifestId id of the manifest to download
   * @param token jwt token of the user
   * @param outputDir path were to save the file
   * @return `Right` nothing if manifest was downloaded or `Left` with an Exception is error occurred
   * */
  override def downloadManifestById(manifestId: String, token: String, outputDir: String):Either[Error, Unit] = {
    val requestUri = new URI(s"$url/$manifestId")
    val httpRequest = new HttpGet(requestUri)
    httpRequest.addHeader(HttpHeaders.AUTHORIZATION, s"Bearer $token")
    httpRequest.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType)
    executeHttpRequestAndDownload(httpRequest, outputDir, manifestId)
  }
}