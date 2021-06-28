package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.IFerload
import ca.ferlab.ferload.client.configurations.{FerloadUrl, UserConfig}
import org.apache.http.HttpResponse
import org.apache.http.client.methods.{HttpGet, HttpRequestBase}
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.util.EntityUtils
import org.json.JSONObject

import java.io.File
import java.net.URL

class FerloadClient(userConfig: UserConfig) extends IFerload {

  val httpBuilder: HttpClientBuilder = HttpClientBuilder.create()
  val http: CloseableHttpClient = httpBuilder.build()
  val url = new URL(userConfig.get(FerloadUrl))
  private val charset = "UTF-8"

  override def getDownloadLink(token: String, manifest: File): String = "download_link"

  override def getConfig: JSONObject = {
    val requestUrl = new URL(url, "/config").toString
    val httpRequest = new HttpGet(requestUrl)
    val response = executeHttpRequest(httpRequest)
    response
  }

  private def executeHttpRequest(request: HttpRequestBase): JSONObject = {
    val response: HttpResponse = http.execute(request)
    val body = response.getStatusLine.getStatusCode match {
      case statusCode if (statusCode >= 200 && statusCode < 299) => Option(response.getEntity)
        .map(e => new JSONObject(EntityUtils.toString(e, charset))).orNull
      case statusCode => throw new RuntimeException(s"Failed to execute request $request, response code $statusCode")
    }
    // always properly close
    EntityUtils.consumeQuietly(response.getEntity)
    body
  }
}
