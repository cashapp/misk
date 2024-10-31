import { MiskWebActionDefinition } from '@web-actions/api/responseTypes';

export default interface MetadataClient {
  fetchMetadata(): Promise<Record<string, MiskWebActionDefinition>>;
}
