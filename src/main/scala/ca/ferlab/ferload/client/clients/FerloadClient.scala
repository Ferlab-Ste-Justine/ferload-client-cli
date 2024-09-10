package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.{LineContent, ManifestContent}
import ca.ferlab.ferload.client.clients.inf.IFerload
import ca.ferlab.ferload.client.configurations.{FerloadUrl, UserConfig}
import org.apache.http.HttpHeaders
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.{ContentType, StringEntity}
import org.json.JSONObject

import java.net.URL

class FerloadClient(userConfig: UserConfig) extends BaseHttpClient with IFerload {

  // lazy because config may be not available
  lazy val url = new URL(userConfig.get(FerloadUrl))
  private val separator = "\n"

  override def getDownloadLinks(token: String, manifestContent: ManifestContent): Either[Error, Map[LineContent, String]]  = {
    val requestUrl = new URL(url, "/objects/list").toString
    val httpRequest = new HttpPost(requestUrl)
    httpRequest.addHeader(HttpHeaders.AUTHORIZATION, s"Bearer $token")
    httpRequest.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType)
    httpRequest.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType)
    val linesAsString = manifestContent.lines.map(_.filePointer).mkString(separator)
    httpRequest.setEntity(new StringEntity(linesAsString))
    val (body, status) = executeHttpRequest(httpRequest)
    status match {
      case 200 => Right(toMap(body, manifestContent.lines))
      case 403 => Left(Error(formatUnauthorizedMessage("The manifest contains files you don't have access to. Please review the following unauthorized file IDs:", body)))
      case _ => Left(Error(formatExceptionMessage("Failed to retrieve download link(s)", status, body)))
    }
  }

  override def getConfig: JSONObject = {
    val requestUrl = new URL(url, "/config").toString
    val httpRequest = new HttpGet(requestUrl)
    val (body, status) = executeHttpRequest(httpRequest)
    if (status != 200) throw new IllegalStateException(formatExceptionMessage("Failed to retrieve Ferload config", status, body))
    body.map(new JSONObject(_)).get // throw exception if null, that's ok
  }

}
