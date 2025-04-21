import { MediaTypes } from '@web-actions/api/MediaTypes';

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
  returnType: string | null;
  pathPattern: string;
  types: MiskObjectTypes;
  requestMediaTypes: string[];
  responseMediaType: string;
  allowedServices: string[];
  allowedCapabilities: string[];
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
  returnType: string | null;
  allowedServices: string[];
  allowedCapabilities: string[];
  callable?: boolean;
}

export type MiskObjectTypes = Record<string, MiskObjectType>;
