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

export interface ActionGroup {
  actionName: string;
  path: string;
  httpMethod: string;
  callables: Record<string, MiskWebActionDefinition>;
  getCallablesByMethod(): MiskWebActionDefinition[];
  all: MiskWebActionDefinition[];
}

export type MiskActions = Record<string, ActionGroup>;

export type MiskObjectTypes = Record<string, MiskObjectType>;
