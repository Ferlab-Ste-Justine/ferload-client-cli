package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.IFerload
import ca.ferlab.ferload.client.configurations.{FerloadUrl, UserConfig}
import org.apache.commons.lang3.StringUtils
import org.apache.http.HttpHeaders
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.{ContentType, StringEntity}
import org.json.JSONObject

import java.io.File
import scala.io.Source

class FerloadClient(userConfig: UserConfig) extends HttpClient with IFerload {

  // lazy because config may be not available
  lazy val url: String = userConfig.get(FerloadUrl)
  private val separator = "\n"

  override def getDownloadLinks(token: String, manifest: File): Map[String, String] = {
    val manifestSource = Source.fromFile(manifest)
    // drop manifest header + properly close the source, send the manifest content as plain text in the POST
    val manifestContent = try manifestSource.getLines().drop(1).filter(StringUtils.isNotBlank)
      .mkString(separator) finally manifestSource.close()
    val requestUrl = s"$url/downloadLinks"
    val httpRequest = new HttpPost(requestUrl)
    httpRequest.addHeader(HttpHeaders.AUTHORIZATION, s"Bearer $token")
    httpRequest.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType)
    httpRequest.setEntity(new StringEntity(manifestContent))
    val (body, status) = executeHttpRequest(httpRequest)
    status match {
      case 200 => toMap(body)
      case 403 => throw new IllegalStateException(formatExceptionMessage("No enough access rights to download the following files", status, body))
      case _ => throw new IllegalStateException(formatExceptionMessage("Failed to retrieve download link(s)", status, body))
    }
  }

  override def getConfig: JSONObject = {
    val requestUrl = s"$url/config"
    val httpRequest = new HttpGet(requestUrl)
    val (body, status) = executeHttpRequest(httpRequest)
    if (status != 200) throw new IllegalStateException(formatExceptionMessage("Failed to retrieve Ferload config", status, body))
    body.map(new JSONObject(_)).get // throw exception if null, that's ok
  }

}
