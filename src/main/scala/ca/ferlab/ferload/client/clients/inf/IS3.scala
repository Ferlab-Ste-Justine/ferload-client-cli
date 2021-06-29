package ca.ferlab.ferload.client.clients.inf

import java.io.File

trait IS3 {
  def download(outputDir: File, links: Map[String, String]): Array[File]
}
