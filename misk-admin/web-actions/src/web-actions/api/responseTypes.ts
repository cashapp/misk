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

export interface ActionGroup {
  name: string;
  defaultCallable?: MiskWebActionDefinition;
  all: MiskWebActionDefinition[];
}

export type MiskActions = Record<string, ActionGroup>;

export type MiskObjectTypes = Record<string, MiskObjectType>;
