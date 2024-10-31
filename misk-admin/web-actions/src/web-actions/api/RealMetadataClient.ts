import {
  MiskActions,
  MiskMetadataResponse,
} from '@web-actions/api/responseTypes';
import { fetchCached } from '@web-actions/network/http';
import { associateBy } from '@web-actions/utils/common';
import MetadataClient from '@web-actions/api/MetadataClient';

export default class RealMetadataClient implements MetadataClient {
  private supportedMediaTypes = new Set([
    'application/x-protobuf',
    'application/grpc',
  ]);

  async fetchMetadata(): Promise<MiskActions> {
    const response = await fetchCached<MiskMetadataResponse>(
      `/api/web-actions/metadata`,
    );
    const actions = response.all['web-actions'].metadata.filter((it) =>
      it.requestMediaTypes.some((mediaType) =>
        this.supportedMediaTypes.has(mediaType),
      ),
    );
    return associateBy(actions, (it) => it.name);
  }
}
