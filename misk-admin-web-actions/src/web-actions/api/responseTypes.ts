import { MediaTypes } from 'src/web-actions/api/MediaTypes';

export interface MiskMetadataResponse {
  all: {
    'web-actions': {
      metadata: MiskWebActionDefinition[];
    };
  };
}

export interface MiskWebActionDefinition {
  name: string;
  httpMethod?: string;
  packageName: string;
  requestType: string | null;
  pathPattern: string;
  types: MiskObjectTypes;
  requestMediaTypes: string[];
  responseMediaType: string;
}

export interface MiskObjectType {
  fields: MiskFieldDefinition[];
}

export interface MiskFieldDefinition {
  name: string;
  type: string;
  repeated: boolean;
  annotations: any[];
}

export interface MiskRoute {
  actionName: string;
  path: string;
  httpMethod: string;
  responseMediaTypes: MediaTypes;
  requestMediaTypes: MediaTypes;
  all: MiskWebActionDefinition[];
  types: MiskObjectTypes;
  requestType: string | null;
  callable?: boolean;
}

export type MiskObjectTypes = Record<string, MiskObjectType>;
