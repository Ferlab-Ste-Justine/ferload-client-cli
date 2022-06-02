package ca.ferlab.ferload.client.clients.inf

import java.io.File

trait IS3 {
  def getTotalAvailableDiskSpaceAt(manifest: File): Long

  def getTotalExpectedDownloadSize(links: Map[String, String], timeout: Long): Long

  def download(outputDir: File, links: Map[String, String]): Set[File]
}
