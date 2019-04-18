import HTTPMethod from "http-method-enum"
import {
  IWebActionAPI,
  IWebActionInternal,
  processMetadata
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
        type: "Double"
      },
      {
        name: "Field 2",
        repeated: false,
        type: "Int"
      },
      {
        name: "Field 3",
        repeated: false,
        type: "Long"
      },
      {
        name: "Field 4",
        repeated: false,
        type: "Short"
      }
    ]
  },
  nestedNoRepeatedInt: {
    fields: [
      {
        name: "Nested Int Field",
        repeated: false,
        type: "noRepeatedInt"
      }
    ]
  },
  nestedRepeatedDouble: {
    fields: [
      {
        name: "Nested Double Field",
        repeated: false,
        type: "repeatedDouble"
      }
    ]
  },
  noRepeatedInt: {
    fields: [
      {
        name: "Int Field",
        repeated: false,
        type: "Int"
      }
    ]
  },
  repeatedDouble: {
    fields: [
      {
        name: "Repeated Double Field",
        repeated: true,
        type: "Double"
      }
    ]
  },
  repeatedNestedNoRepeatedInt: {
    fields: [
      {
        name: "Repeated Nested Int Field",
        repeated: true,
        type: "nestedNoRepeatedInt"
      }
    ]
  },
  repeatedNestedRepeatedDouble: {
    fields: [
      {
        name: "Nested Repeated Double Field",
        repeated: true,
        type: "nestedRepeatedDouble"
      }
    ]
  }
}

export const simpleForm = {
  simpleTag: "simpleForm"
}

export const nonTypedActionAPI: IWebActionAPI = {
  allowedRoles: [] as string[],
  allowedServices: [] as string[],
  applicationInterceptors: [] as string[],
  dispatchMechanism: HTTPMethod.GET,
  function:
    "fun misk.web.actions.LivenessCheckAction.livenessCheck(): misk.web.Response<kotlin.String>",
  functionAnnotations: [
    "@misk.web.Get(pathPattern=/_liveness)",
    "@misk.web.ResponseContentType(value=text/plain;charset=utf-8)",
    "@misk.security.authz.Unauthenticated()"
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
    "misk.web.interceptors.WideOpenDevelopmentInterceptor"
  ],
  parameterTypes: [] as string[],
  pathPattern: "/_liveness",
  requestMediaTypes: ["*/*"],
  responseMediaType: "text/plain;charset=utf-8",
  returnType: "misk.web.Response<kotlin.String>"
}

export const nonTypedActionInternal: IWebActionInternal = processMetadata([
  nonTypedActionAPI
])[0]
