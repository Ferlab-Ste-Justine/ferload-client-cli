package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.IS3
import com.typesafe.config.Config
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet

import java.io._
import java.util.concurrent.Executors
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}

class S3Client(appConfig: Config) extends BaseHttpClient with IS3 {

  private implicit val executorContext: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(appConfig.getInt("download-files-pool")))
  sys.addShutdownHook(executorContext.shutdown())

  private val bufferSize = appConfig.getInt("download-files-buffer")

  override def download(outputDir: File, links: Map[String, String]): Set[File] = {
    val downloads = Future.traverse(links.keySet)(fileName => {
      val link = links(fileName)
      Future(download(outputDir, fileName, link))
    })
    Await.result(downloads, Duration.Inf)
  }

  private def download(outputDir: File, fileName: String, link: String)
                      (implicit executorContext: ExecutionContextExecutorService): File = {
    val file = new File(outputDir.getAbsolutePath + File.separator + fileName)
    val request = new HttpGet(link)
    val response: HttpResponse = http.execute(request)
    val consumer = new S3ChunkConsumer(file, response, bufferSize)
    consumer.waitForCompletion()
    file
  }
}