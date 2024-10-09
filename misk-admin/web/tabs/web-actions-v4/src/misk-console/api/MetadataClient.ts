import { MiskWebActionDefinition } from "@misk-console/api/responseTypes"

export default interface MetadataClient {
  fetchMetadata(): Promise<Record<string, MiskWebActionDefinition>>
}
