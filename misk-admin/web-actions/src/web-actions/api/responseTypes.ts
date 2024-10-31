export interface MiskMetadataResponse {
  all: {
    'web-actions': {
      metadata: MiskWebActionDefinition[];
    };
  };
}

export interface MiskWebActionDefinition {
  name: string;
  requestType: string;
  pathPattern: string;
  types: MiskObjectTypes;
  requestMediaTypes: string[];
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

export type MiskActions = Record<string, MiskWebActionDefinition>;

export type MiskObjectTypes = Record<string, MiskObjectType>;
