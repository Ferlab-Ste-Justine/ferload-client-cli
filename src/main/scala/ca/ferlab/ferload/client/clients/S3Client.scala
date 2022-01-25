package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.IS3
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.PresignedUrlDownloadRequest
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import org.apache.http.HttpHeaders

import java.io.File
import java.net.URL
import java.nio.file.{Files, Paths}
import java.util.concurrent.Executors
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}

class S3Client(nThreads: Int = 1) extends IS3 {

  private implicit val executorContext: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(nThreads))
  sys.addShutdownHook(executorContext.shutdown())

  private val awsClient = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build()
  private val trx = TransferManagerBuilder.standard().withS3Client(awsClient).build()
  sys.addShutdownHook(trx.shutdownNow())

  override def getTotalExpectedDownloadSize(links: Map[String, String]): Long = {
    links.map({ case (_, link) =>
      new URL(link).openConnection.getHeaderField(HttpHeaders.CONTENT_LENGTH).toLong
    }).sum
  }

  override def download(outputDir: File, links: Map[String, String]): Set[File] = {
    val padding = links.keySet.max.length + 1
    val downloads = Future.traverse(links.keySet)(fileName => {
      val link = links(fileName)
      Future(download(outputDir, fileName, link, padding))
    })
    Await.result(downloads, Duration.Inf)
  }

  private def download(outputDir: File, fileName: String, link: String, padding: Int): File = {
    val file = new File(outputDir.getAbsolutePath + File.separator + fileName)
    val request = new PresignedUrlDownloadRequest(new URL(link))
    val download = trx.download(request, file)
    val progressListener = new S3TransferProgressListener(fileName, download, padding)
    download.addProgressListener(progressListener)
    download.waitForCompletion()
    progressListener.stop()
    file
  }

  override def getTotalAvailableDiskSpaceAt(manifest: File): Long = {
    Files.getFileStore(Paths.get(manifest.getAbsolutePath)).getUsableSpace
  }
}