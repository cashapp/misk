import {
  MiskActions,
  MiskMetadataResponse
} from "@misk-console/api/responseTypes"
import { fetchCached } from "@misk-console/network/http"
import { associateBy } from "@misk-console/utils/common"
import MetadataClient from "@misk-console/api/MetadataClient"

export default class RealMetadataClient implements MetadataClient {

  private supportedMediaTypes = new Set(['application/x-protobuf', 'application/grpc']);

  async fetchMetadata(): Promise<MiskActions> {
    const response = await fetchCached<MiskMetadataResponse>(`/api/web-actions/metadata`);
    const actions =
      response.all['web-actions'].metadata
        .filter(it =>
          it.requestMediaTypes.some(mediaType => this.supportedMediaTypes.has(mediaType))
        );
    return associateBy(actions, it => it.name)
  }
}
