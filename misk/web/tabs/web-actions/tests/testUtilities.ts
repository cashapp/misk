import HTTPMethod from "http-method-enum"
import {
  IWebActionAPI,
  IWebActionInternal,
  processMetadata,
} from "../src/ducks"

/**
 * Test Constants
 */
export const testTypes = {
  multipleFlatFields: {
    fields: [
      {
        name: "Field 1",
        repeated: false,
        type: "Short",
      },
      {
        name: "Field 2",
        repeated: false,
        type: "Int",
      },
      {
        name: "Field 3",
        repeated: false,
        type: "Long",
      },
      {
        name: "Field 4",
        repeated: false,
        type: "Short",
      },
    ],
  },
  nestedNoRepeatedInt: {
    fields: [
      {
        name: "Nested Int Field",
        repeated: false,
        type: "noRepeatedInt",
      },
    ],
  },
  nestedRepeatedShort: {
    fields: [
      {
        name: "Nested Short Field",
        repeated: false,
        type: "repeatedShort",
      },
    ],
  },
  noRepeatedInt: {
    fields: [
      {
        name: "Int Field",
        repeated: false,
        type: "Int",
      },
    ],
  },
  repeatedShort: {
    fields: [
      {
        name: "Repeated Short Field",
        repeated: true,
        type: "Short",
      },
    ],
  },
  repeatedNestedNoRepeatedInt: {
    fields: [
      {
        name: "Repeated Nested Int Field",
        repeated: true,
        type: "nestedNoRepeatedInt",
      },
    ],
  },
  repeatedNestedRepeatedShort: {
    fields: [
      {
        name: "Nested Repeated Short Field",
        repeated: true,
        type: "nestedRepeatedShort",
      },
    ],
  },
}

export const simpleForm = {
  simpleTag: "simpleForm",
}

export const nonTypedActionAPI: IWebActionAPI = {
  allowedCapabilities: [] as string[],
  allowedServices: [] as string[],
  applicationInterceptors: [] as string[],
  httpMethod: HTTPMethod.GET,
  function:
    "fun misk.web.actions.LivenessCheckAction.livenessCheck(): misk.web.Response<kotlin.String>",
  functionAnnotations: [
    "@misk.web.Get(pathPattern=/_liveness)",
    "@misk.web.ResponseContentType(value=text/plain;charset=utf-8)",
    "@misk.security.authz.Unauthenticated()",
  ],
  name: "LivenessCheckAction",
  networkInterceptors: [
    "misk.web.interceptors.InternalErrorInterceptorFactory$Companion$INTERCEPTOR$1",
    "misk.web.interceptors.RequestLogContextInterceptor",
    "misk.web.interceptors.RequestLoggingInterceptor",
    "misk.web.interceptors.MetricsInterceptor",
    "misk.web.interceptors.TracingInterceptor",
    "misk.web.exceptions.ExceptionHandlingInterceptor",
    "misk.web.interceptors.MarshallerInterceptor",
    "misk.web.interceptors.WideOpenDevelopmentInterceptor",
  ],
  parameterTypes: [] as string[],
  pathPattern: "/_liveness",
  requestMediaTypes: ["*/*"],
  responseMediaType: "text/plain;charset=utf-8",
  returnType: "misk.web.Response<kotlin.String>",
}

export const nonTypedActionInternal: IWebActionInternal = processMetadata([
  nonTypedActionAPI,
])[0]
