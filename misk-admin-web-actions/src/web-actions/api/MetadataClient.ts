import { MiskRoute } from '@web-actions/api/responseTypes';

export default interface MetadataClient {
  fetchMetadata(): Promise<MiskRoute[]>;
}
