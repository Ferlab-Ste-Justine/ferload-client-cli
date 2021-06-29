package ca.ferlab.ferload.client.clients

import ca.ferlab.ferload.client.clients.inf.IS3
import ca.ferlab.ferload.client.commands.factory.CommandBlock
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.PresignedUrlDownloadRequest
import com.amazonaws.services.s3.transfer.TransferManagerBuilder

import java.io.File
import java.net.URL

class S3Client extends IS3 {

  private val awsClient = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1).build()
  private val trx = TransferManagerBuilder.standard().withS3Client(awsClient).build()
  private val packageEmoji = new String(Character.toChars(0x1F4E6))

  override def download(outputDir: File, links: Map[String, String]): Array[File] = {
    links.map(link => download(outputDir, link._1, link._2)).toArray
  }

  private def download(outputDir: File, fileName: String, link: String): File = {
    val file = new File(outputDir.getAbsolutePath + File.separator + fileName)
    val request = new PresignedUrlDownloadRequest(new URL(link))
    new CommandBlock[Unit](s"${file.getAbsolutePath} ...", packageEmoji) {
      override def run(): Unit = {
        val download = trx.download(request, file)
        download.waitForCompletion()
      }
    }.execute()
    file
  }
}