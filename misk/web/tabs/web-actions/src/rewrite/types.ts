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
  packageName: string
  description: string
  functionAnnotations: string[]
  requestMediaTypes: string[]
  responseMediaType: string
  parameterTypes: string[]
  parameters: ParameterMetaData[]
  requestType: string
  returnType: string
  responseType: string
  types: { [key: string]: ProtoType }
  responseTypes: { [key: string]: ProtoType }
  pathPattern: string
  applicationInterceptors: string[]
  networkInterceptors: string[]
  httpMethod: string
  allowedServices: string[]
  allowedCapabilities: string[]
}

export interface ParameterMetaData {
  name: string
  annotations: string[]
  type: string
}

export interface WebActionMetadataResponse {
  protobufDocUrlPrefix: string
  webActionMetadata: WebActionMetadata[]
}

export type WebActionsByPackage = { [packageName: string]: WebActionMetadata[] }
