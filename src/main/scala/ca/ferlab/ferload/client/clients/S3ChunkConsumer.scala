package ca.ferlab.ferload.client.clients

import org.apache.commons.codec.digest.DigestUtils
import org.apache.http.util.EntityUtils
import org.apache.http.{HttpHeaders, HttpResponse}

import java.io.{File, FileInputStream, FileOutputStream}
import scala.util.Using

class S3ChunkConsumer(file: File, response: HttpResponse, bufferSize: Int) {

  private val totalSize = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH).getValue.toLong
  private val checksum = response.getFirstHeader(HttpHeaders.ETAG).getValue.replace("\"", "")
  private val fileName = file.getName

  private def verifyFile(): Unit = {
    // apache DigestUtils better at memory consumption than any others tested solutions
    Using.Manager { use =>
      val fis = use(new FileInputStream(file))
      val digest = DigestUtils.md5Hex(fis) // always MD5
      if (!digest.equals(checksum)) throw new IllegalStateException(s"Invalid checksum for file $file expected: $checksum  but computed $digest)")
    }
    val fileLength = file.length()
    if (fileLength != totalSize) throw new IllegalStateException(s"Invalid file length $file expected: $totalSize  but computed $fileLength)")
  }

  def waitForCompletion(): Unit = {
    val buffer: Array[Byte] = Array.ofDim[Byte](bufferSize)
    var totalReceived = 0

    Using.Manager { use =>
      val inputStream = use(response.getEntity.getContent)
      val outputStream = use(new FileOutputStream(file))
      Iterator
        .continually(inputStream.read(buffer))
        .takeWhile(_ != -1)
        .foreach({
          currentReceived =>
            outputStream.write(buffer, 0, currentReceived)
            totalReceived += currentReceived
            ConsoleProgressBar.displayProgressBar(fileName, totalReceived, totalSize)
        })

      EntityUtils.consumeQuietly(response.getEntity)
      verifyFile()
    }
  }
}