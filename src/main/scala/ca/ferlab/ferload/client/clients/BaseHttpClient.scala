package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.LineContent
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.util.EntityUtils
import org.json.JSONObject

import scala.jdk.CollectionConverters.MapHasAsScala // scala 2.13
// import scala.collection.JavaConverters._ // scala 2.12

abstract class BaseHttpClient {

  protected val httpBuilder: HttpClientBuilder = HttpClientBuilder.create()
  protected val http: CloseableHttpClient = httpBuilder.build()
  protected val charset = "UTF-8"

  sys.addShutdownHook(http.close())

  protected def executeHttpRequest(request: HttpRequestBase): (Option[String], Int) = {
    val response: HttpResponse = http.execute(request)
    val body = Option(response.getEntity).map(e => EntityUtils.toString(e, charset))
    // always properly close
    EntityUtils.consumeQuietly(response.getEntity)
    (body, response.getStatusLine.getStatusCode)
  }

  protected def formatExceptionMessage(message: String, status: Int, body: Option[String]): String = {
    s"$message, code: $status, message:\n${body.getOrElse("")}"
  }

  protected def toMap(body: Option[String], lineContents: Seq[LineContent]): Map[LineContent, String] = {
    body.map(new JSONObject(_).toMap.asScala.map({ case (key, value) =>
      lineContents.find(_.filePointer == key).get -> value.toString
    }).toMap).getOrElse(Map())
  }
}
