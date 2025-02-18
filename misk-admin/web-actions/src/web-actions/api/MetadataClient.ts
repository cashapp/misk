import { MiskActions } from '@web-actions/api/responseTypes';

export default interface MetadataClient {
  fetchMetadata(): Promise<MiskActions>;
}
