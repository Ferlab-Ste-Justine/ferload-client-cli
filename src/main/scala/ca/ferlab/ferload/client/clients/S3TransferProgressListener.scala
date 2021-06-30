package ca.ferlab.ferload.client.clients

import com.amazonaws.event.{ProgressEvent, ProgressEventType, ProgressListener}
import com.amazonaws.services.s3.transfer.Transfer
import me.tongfei.progressbar.{ProgressBar, ProgressBarStyle}

class S3TransferProgressListener(fileName: String, transfer: Transfer) extends ProgressListener {

  private val totalBytes: Long = transfer.getProgress.getTotalBytesToTransfer
  private val pb = new ProgressBar(fileName, totalBytes, 100, System.out, ProgressBarStyle.ASCII, "", 1)

  def stop(): Unit = {
    pb.stepTo(totalBytes)
    pb.close()
  }

  override def progressChanged(progressEvent: ProgressEvent): Unit = {
    progressEvent.getEventType match {
      case ProgressEventType.RESPONSE_BYTE_TRANSFER_EVENT => pb.stepBy(progressEvent.getBytesTransferred)
      case _ =>
    }
  }
}
