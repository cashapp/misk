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

export interface ActionGroup {
  actionName: string;
  path: string;
  httpMethod: string;
  responseMediaTypes: string[];
  requestMediaTypes: string[];
  canCall: boolean;
  callables: MiskWebActionDefinition[];
  all: MiskWebActionDefinition[];
  types: MiskObjectTypes;
  requestType: string | null;
}

export type MiskActions = Record<string, ActionGroup>;

export type MiskObjectTypes = Record<string, MiskObjectType>;
