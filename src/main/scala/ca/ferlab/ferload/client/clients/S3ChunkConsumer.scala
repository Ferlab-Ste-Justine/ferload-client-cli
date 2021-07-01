package ca.ferlab.ferload.client.clients

import org.apache.http.util.EntityUtils
import org.apache.http.{HttpHeaders, HttpResponse}

import java.io.{File, FileOutputStream}

class S3ChunkConsumer(file: File, response: HttpResponse, bufferSize: Int = 1024) {

  private val totalSize = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH).getValue.toLong
  private val fileName = file.getName

  private val inputStream = response.getEntity.getContent
  private val outputStream = new FileOutputStream(file)

  def waitForCompletion(): Unit = {
    val buffer: Array[Byte] = Array.ofDim[Byte](bufferSize)
    var totalReceived = 0

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
    inputStream.close()
    outputStream.close()
  }
}