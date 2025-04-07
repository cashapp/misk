import {
  MiskMetadataResponse,
  MiskRoute,
} from '@web-actions/api/responseTypes';
import { fetchCached } from '@web-actions/network/http';
import MetadataClient from '@web-actions/api/MetadataClient';
import { buildRoutes } from 'src/web-actions/api/BuildRoutes';

export default class RealMetadataClient implements MetadataClient {
  async fetchMetadata(): Promise<MiskRoute[]> {
    return fetchCached<MiskMetadataResponse>(`/api/web-actions/metadata`).then(
      (it) => buildRoutes(it.all['web-actions'].metadata),
    );
  }
}
