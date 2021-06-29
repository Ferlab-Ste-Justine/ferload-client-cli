package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.IFerload
import ca.ferlab.ferload.client.configurations.{FerloadUrl, UserConfig}
import org.apache.commons.lang3.StringUtils
import org.apache.http.client.methods.{HttpGet, HttpPost, HttpRequestBase}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.util.EntityUtils
import org.apache.http.{HttpHeaders, HttpResponse}
import org.json.JSONObject

import java.io.File
import java.net.URL
import scala.io.Source
import scala.jdk.CollectionConverters.MapHasAsScala

class FerloadClient(userConfig: UserConfig) extends IFerload {

  val httpBuilder: HttpClientBuilder = HttpClientBuilder.create()
  val http: CloseableHttpClient = httpBuilder.build()
  val url = new URL(userConfig.get(FerloadUrl))
  private val charset = "UTF-8"
  private val separator = "\n"

  override def getDownloadLinks(token: String, manifest: File): Map[String, String] = {
    val manifestSource = Source.fromFile(manifest)
    // drop manifest header + properly close the source, send the manifest content as plain text in the POST
    val manifestContent = try manifestSource.getLines().drop(1).filter(StringUtils.isNotBlank)
      .mkString(separator) finally manifestSource.close()
    val requestUrl = new URL(url, "/downloadLinks").toString
    val httpRequest = new HttpPost(requestUrl)
    httpRequest.addHeader(HttpHeaders.AUTHORIZATION, s"Bearer $token")
    httpRequest.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType)
    httpRequest.setEntity(new StringEntity(manifestContent))
    val (body, status) = executeHttpRequest(httpRequest)
    status match {
      case 200 => toMap(body)
      case 403 => throw new IllegalStateException(formatExceptionMessage("No enough access rights to download the following files: ", status, body))
      case _ => throw new IllegalStateException(formatExceptionMessage("Failed to retrieve download link(s)", status, body))
    }
  }

  override def getConfig: JSONObject = {
    val requestUrl = new URL(url, "/config").toString
    val httpRequest = new HttpGet(requestUrl)
    val (body, status) = executeHttpRequest(httpRequest)
    if (status != 200) throw new IllegalStateException(formatExceptionMessage("Failed to retrieve Ferload config", status, body))
    new JSONObject(body)
  }

  private def executeHttpRequest(request: HttpRequestBase): (String, Int) = {
    val response: HttpResponse = http.execute(request)
    val body = Option(response.getEntity).map(e => EntityUtils.toString(e, charset)).orNull
    // always properly close
    EntityUtils.consumeQuietly(response.getEntity)
    (body, response.getStatusLine.getStatusCode)
  }

  private def formatExceptionMessage(message: String, status: Int, reason: String) = {
    s"$message, code: $status, message:\n$reason"
  }

  private def toMap(body: String): Map[String, String] = {
    new JSONObject(body).toMap.asScala.map(entry => (entry._1, entry._2.toString)).toMap
  }

}
