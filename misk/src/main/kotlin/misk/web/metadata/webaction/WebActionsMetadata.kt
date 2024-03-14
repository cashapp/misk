package misk.web.metadata.webaction

import misk.web.metadata.Metadata

data class WebActionsMetadata(
  override val metadata: List<WebActionMetadata>,
) : Metadata<List<WebActionMetadata>> {
  override val id: String = "WebActionsMetadata"
  override val metadataClass: Class<List<WebActionMetadata>> =
    List::class.java as Class<List<WebActionMetadata>>
}
