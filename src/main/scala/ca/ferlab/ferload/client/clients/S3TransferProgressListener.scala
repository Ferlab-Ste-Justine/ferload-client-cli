package ca.ferlab.ferload.client.clients

import com.amazonaws.event.{ProgressEvent, ProgressEventType, ProgressListener}
import com.amazonaws.services.s3.transfer.Transfer

class S3TransferProgressListener(fileName: String, transfer: Transfer) extends ProgressListener {

  private val totalBytes: Long = transfer.getProgress.getTotalBytesToTransfer
  private var totalReceived = 0L

  def stop(): Unit = {
    if (totalReceived < totalBytes) {
      ConsoleProgressBar.displayProgressBar(fileName, totalBytes, totalBytes)
    }
  }

  override def progressChanged(progressEvent: ProgressEvent): Unit = {
    progressEvent.getEventType match {
      case ProgressEventType.RESPONSE_BYTE_TRANSFER_EVENT => {
        totalReceived += progressEvent.getBytesTransferred
        ConsoleProgressBar.displayProgressBar(fileName, totalReceived, totalBytes, displayOnlyIfMultipleOf = 1)
      }
      case _ =>
    }
  }
}
