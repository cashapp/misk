export interface ProtoField {
  name: string
  type: string
  repeated: boolean
}

export interface ProtoType {
  fields: ProtoField[]
}

export interface WebActionMetadata {
  name: string
  function: string
  functionAnnotations: string[]
  requestMediaTypes: string[]
  responseMediaType: string
  parameterTypes: string[]
  requestType: string
  returnType: string
  types: { [key: string]: ProtoType }
  pathPattern: string
  applicationInterceptors: string[]
  networkInterceptors: string[]
  httpMethod: string
  allowedServices: string[]
  allowedCapabilities: string[]
}

export interface WebActionMetadataResponse {
  webActionMetadata: WebActionMetadata[]
}
