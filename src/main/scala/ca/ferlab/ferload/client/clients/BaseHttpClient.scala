package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.LineContent
import org.apache.http.{HttpEntity, HttpResponse}
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.util.EntityUtils
import org.json.JSONObject

import java.io.{File, FileOutputStream}
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.util.{Failure, Success, Try} // scala 2.13

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

  protected def executeHttpRequestAndDownload(request: HttpRequestBase, path: String, manifestId: String): Either[Error, Unit] = {
    val response: HttpResponse = http.execute(request)
    val extraction = Option(response.getEntity).map(e =>
      response.getStatusLine.getStatusCode match {
        case status if status < 300 => extractEntityLinesToFile(e, s"$path/$manifestId.tsv")
        case status => Left(Error(s"Failed to retrieve manifest for id: $manifestId, code: $status"))
      }
    ).getOrElse(Left(Error("Unknown Response status")))

    // always properly close
    EntityUtils.consumeQuietly(response.getEntity)

    extraction
  }

  private def extractEntityLinesToFile(entity: HttpEntity, filePath: String): Either[Error, Unit] = {
    Try {
      val manifestFile = new File(filePath)
      val outputStream: FileOutputStream = new FileOutputStream(manifestFile)
      try entity.writeTo(outputStream)
      finally if (outputStream != null) outputStream.close()
    } match {
      case Success(_) => Right()
      case Failure(e) => Left(Error(e.getMessage))
    }
  }

  protected def formatExceptionMessage(message: String, status: Int, body: Option[String]): String = {
    val msg = body.map(r => new JSONObject(r).get("msg"))

    s"$message, code: $status, message:\n${msg.getOrElse("")}"
  }

  protected def formatUnauthorizedMessage(message: String, body: Option[String]): String = {
    val msg = body.map(r => new JSONObject(r).get("msg"))

    s"$message\n${msg.getOrElse("")}"
  }


  protected def toMap(body: Option[String], lineContents: Seq[LineContent]): Map[LineContent, String] = {
    body.map(new JSONObject(_).toMap.asScala.map({ case (key, value) =>
      lineContents.find(_.filePointer == key).get -> value.toString
    }).toMap).getOrElse(Map())
  }
}
