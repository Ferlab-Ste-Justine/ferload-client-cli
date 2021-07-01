package ca.ferlab.ferload.client.clients

import org.apache.http.HttpResponse
import org.apache.http.util.EntityUtils

import java.io.{File, FileOutputStream}

class S3ChunkConsumer(file: File, response: HttpResponse, bufferSize: Int = 1024) {

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
      })

    EntityUtils.consumeQuietly(response.getEntity)
    inputStream.close()
    outputStream.close()
  }

}
