import {
  MiskActions,
  MiskMetadataResponse
} from "@misk-console/api/responseTypes"
import { fetchCached } from "@misk-console/network/http"
import { associateBy } from "@misk-console/utils/common"
import MetadataClient from "@misk-console/api/MetadataClient"

export default class RealMetadataClient implements MetadataClient {
  async fetchMetadata(): Promise<MiskActions> {
    return fetchCached<MiskMetadataResponse>(
      `/api/web-actions/metadata`
    )
      .then(it => it.all["web-actions"].metadata)
      .then(it => associateBy(it, it => it.name))
  }
}
