export interface Tab {
  slug: string
  url_path_prefix: string
  dashboard_slug: string
  name: string
  category: string
  capabilities: string[]
  services: string[]
}

export interface DashboardMetadata {
  home_url: string
  navbar_items: string[]
  navbar_status: string
  tabs: Tab[]
}

export interface DashboardMetadataResponse {
  dashboardMetadata: DashboardMetadata
}

export interface ServiceMetadata {
  app_name: string
  environment: string
}

export interface ServiceMetadataResponse {
  serviceMetadata: ServiceMetadata
}

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
