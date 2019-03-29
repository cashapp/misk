import {
  createAction,
  defaultRootState,
  IAction,
  IRootState
} from "@misk/simpleredux"
import axios from "axios"
import { HTTPMethod } from "http-method-enum"
import { chain } from "lodash"
import { all, AllEffect, call, put, takeLatest } from "redux-saga/effects"

export const enum TypescriptBaseTypes {
  "any" = "any",
  "boolean" = "boolean",
  "enum" = "enum",
  "null" = "null",
  "number" = "number",
  "string" = "string"
}

export const enum KotlinTypes {
  "Boolean" = "Boolean",
  "Byte" = "Byte",
  "ByteString" = "ByteString",
  "Char" = "Char",
  "Double" = "Double",
  "Int" = "Int",
  "Long" = "Long",
  "Short" = "Short",
  "String" = "String"
}

export interface IBaseFieldTypes {
  [kotlinType: string]: TypescriptBaseTypes
}

export const BaseFieldTypes: IBaseFieldTypes = {
  [KotlinTypes.Boolean]: TypescriptBaseTypes.boolean,
  [KotlinTypes.Short]: TypescriptBaseTypes.number,
  [KotlinTypes.Int]: TypescriptBaseTypes.number,
  [KotlinTypes.Long]: TypescriptBaseTypes.number,
  [KotlinTypes.Double]: TypescriptBaseTypes.number,
  [KotlinTypes.ByteString]: TypescriptBaseTypes.string,
  [KotlinTypes.String]: TypescriptBaseTypes.string
}

export interface IFieldTypeMetadata {
  name: string
  repeated: boolean
  type: IBaseFieldTypes | any
}

export interface IActionTypes {
  [type: string]: {
    fields: IFieldTypeMetadata[]
  }
}

export interface IWebActionAPI {
  allowedServices: string[]
  allowedRoles: string[]
  applicationInterceptors: string[]
  dispatchMechanism: HTTPMethod
  function: string
  functionAnnotations: string[]
  name: string
  networkInterceptors: string[]
  parameterTypes: string[]
  pathPattern: string
  requestMediaTypes: string[]
  responseMediaType: string
  returnType: string
  requestType: string
  types: IActionTypes
}

export interface IWebActionInternal {
  allFields: string
  allowedRoles: string
  allowedServices: string
  applicationInterceptors: string[]
  authFunctionAnnotations: string[]
  dispatchMechanism: HTTPMethod[]
  function: string
  functionAnnotations: string[]
  name: string
  networkInterceptors: string[]
  nonAccessOrTypeFunctionAnnotations: string[]
  parameterTypes: string[]
  pathPattern: string
  requestMediaTypes: string[]
  responseMediaType: string
  returnType: string
  requestType: string
  types: IActionTypes
}

/**
 * Titlecase versions of IWebActionInternal fields for use in Filter UI
 */
export const WebActionInternalLabel: { [key: string]: string } = {
  "All Metadata": "allFields",
  "Allowed Roles": "allowedRoles",
  "Allowed Services": "allowedServices",
  "Application Interceptor": "applicationInterceptors",
  "Dispatch Mechanism": "dispatchMechanism",
  Function: "function",
  "Function Annotations": "functionAnnotations",
  Name: "name",
  "Network Interceptor": "networkInterceptors",
  "Parameter Types": "parameterTypes",
  "Path Pattern": "pathPattern",
  "Request Type": "requestMediaTypes",
  "Response Type": "responseMediaType"
}

/**
 * Actions
 * string enum of the defined actions that is used as type enforcement for Reducer and Sagas arguments
 */
export enum WEBACTIONS {
  DINOSAUR = "WEBACTIONS_DINOSAUR",
  METADATA = "WEBACTIONS_METADATA",
  SUCCESS = "WEBACTIONS_SUCCESS",
  FAILURE = "WEBACTIONS_FAILURE"
}

/**
 * Dispatch Object
 * Object of functions that dispatch Actions with standard defaults and any required passed in input
 * dispatch Object is used within containers to initiate any saga provided functionality
 */
export interface IWebActionsPayload {
  data?: any
  error: any
  loading: boolean
  success: boolean
}

export interface IDispatchWebActions {
  webActionsDinosaur: (
    data: any,
    fieldTag: string,
    formTag: string
  ) => IAction<WEBACTIONS.DINOSAUR, IWebActionsPayload>
  webActionsFailure: (
    error: any
  ) => IAction<WEBACTIONS.FAILURE, IWebActionsPayload>
  webActionsMetadata: () => IAction<WEBACTIONS.METADATA, IWebActionsPayload>
  webActionsSuccess: (
    data: any
  ) => IAction<WEBACTIONS.SUCCESS, IWebActionsPayload>
}

export const dispatchWebActions: IDispatchWebActions = {
  webActionsDinosaur: () =>
    createAction<WEBACTIONS.DINOSAUR, IWebActionsPayload>(WEBACTIONS.DINOSAUR, {
      error: null,
      loading: true,
      success: false
    }),
  webActionsFailure: (error: any) =>
    createAction<WEBACTIONS.FAILURE, IWebActionsPayload>(WEBACTIONS.FAILURE, {
      ...error,
      loading: false,
      success: false
    }),
  webActionsMetadata: () =>
    createAction<WEBACTIONS.METADATA, IWebActionsPayload>(WEBACTIONS.METADATA, {
      error: null,
      loading: true,
      success: false
    }),
  webActionsSuccess: (data: any) =>
    createAction<WEBACTIONS.SUCCESS, IWebActionsPayload>(WEBACTIONS.SUCCESS, {
      ...data,
      error: null,
      loading: false,
      success: true
    })
}

/**
 * Sagas are generating functions that consume actions and
 * pass either latest (takeLatest) or every (takeEvery) action
 * to a handling generating function.
 *
 * Handling function is where obtaining web resources is done
 * Web requests are done within try/catch so that
 *  if request fails: a failure action is dispatched
 *  if request succeeds: a success action with the data is dispatched
 * Further processing of the data should be minimized within the handling
 *  function to prevent unhelpful errors. Ie. a failed request error is
 *  returned but it actually was just a parsing error within the try/catch.
 */
function* handleDinosaur(action: IAction<WEBACTIONS, IWebActionsPayload>) {
  try {
    const { data } = yield call(
      axios.get,
      "https://jsonplaceholder.typicode.com/posts/"
    )
    yield put(dispatchWebActions.webActionsSuccess({ data }))
  } catch (e) {
    yield put(dispatchWebActions.webActionsFailure({ error: { ...e } }))
  }
}

/**
 * hash for groupBy that provides a string hash that uses an aggregate of
 * non-dispatchMechanism metadata. This allows coalescing of web action entries
 * that only differ by dispatchMechanism (GET, POST, PUT...)
 */
const groupByWebActionHash = (
  action: IWebActionInternal | IWebActionAPI
): string =>
  "" +
  action.pathPattern +
  action.function +
  action.functionAnnotations +
  action.applicationInterceptors +
  action.networkInterceptors +
  action.parameterTypes +
  action.requestMediaTypes +
  action.responseMediaType +
  action.returnType

function* handleMetadata() {
  try {
    const { data } = yield call(axios.get, "/api/webaction/metadata")
    const { webActionMetadata } = data
    const metadata = chain(webActionMetadata)
      .map((action: IWebActionAPI) => {
        const authFunctionAnnotations = action.functionAnnotations.filter(
          a => a.includes("Access") || a.includes("authz")
        )
        const nonAccessOrTypeFunctionAnnotations = action.functionAnnotations.filter(
          a =>
            !(
              a.includes("RequestContentType") ||
              a.includes("ResponseContentType") ||
              a.includes("Access") ||
              a.includes("authz") ||
              a.toUpperCase().includes(HTTPMethod.DELETE) ||
              a.toUpperCase().includes(HTTPMethod.GET) ||
              a.toUpperCase().includes(HTTPMethod.HEAD) ||
              a.toUpperCase().includes(HTTPMethod.PATCH) ||
              a.toUpperCase().includes(HTTPMethod.POST) ||
              a.toUpperCase().includes(HTTPMethod.PUT)
            )
        )
        const emptyAllowedArrayValue =
          authFunctionAnnotations.length > 0 &&
          authFunctionAnnotations[0].includes("Unauthenticated")
            ? "All"
            : "None"
        const allowedRoles =
          action.allowedRoles.length > 0
            ? action.allowedRoles.join(", ")
            : emptyAllowedArrayValue

        const allowedServices =
          action.allowedServices.length > 0
            ? action.allowedServices.join(", ")
            : emptyAllowedArrayValue

        return {
          ...action,
          allFields: JSON.stringify(action),
          allowedRoles,
          allowedServices,
          authFunctionAnnotations,
          dispatchMechanism: [action.dispatchMechanism],
          function: action.function.split("fun ").pop(),
          nonAccessOrTypeFunctionAnnotations
        }
      })
      .groupBy(groupByWebActionHash)
      .map((actions: IWebActionInternal[]) => {
        const dispatchMechanism = chain(actions)
          .flatMap(action => action.dispatchMechanism)
          // remove duplicate identical dispatchMechanisms that come from
          // duplicate installation of the same webAction
          .uniq()
          .value()
        const mergedAction = actions[0]
        mergedAction.dispatchMechanism = dispatchMechanism.sort().reverse()
        return mergedAction
      })
      .sortBy(["name", "pathPattern"])
      .value()
    // TODO(adrw) build index of keyspace for filterable fields

    yield put(dispatchWebActions.webActionsSuccess({ metadata }))
  } catch (e) {
    yield put(dispatchWebActions.webActionsFailure({ error: { ...e } }))
  }
}

export function* watchWebActionsSagas(): IterableIterator<AllEffect> {
  yield all([
    takeLatest(WEBACTIONS.DINOSAUR, handleDinosaur),
    takeLatest(WEBACTIONS.METADATA, handleMetadata)
  ])
}

/**
 * Initial State
 * Reducer merges all changes from dispatched action objects on to this initial state
 */
const initialState = defaultRootState("webActions")

/**
 * Duck Reducer
 * Merges dispatched action objects on to the existing (or initial) state to generate new state
 */
export const WebActionsReducer = (
  state = initialState,
  action: IAction<WEBACTIONS, {}>
) => {
  switch (action.type) {
    case WEBACTIONS.DINOSAUR:
    case WEBACTIONS.FAILURE:
    case WEBACTIONS.METADATA:
    case WEBACTIONS.SUCCESS:
      return state.merge(action.payload)
    default:
      return state
  }
}

/**
 * State Interface
 * Provides a complete Typescript interface for the object on state that this duck manages
 * Consumed by the root reducer in ./ducks index to update global state
 * Duck state is attached at the root level of global state
 */
export interface IWebActionsState extends IRootState {
  metadata: IWebActionInternal
  [key: string]: any
}

export interface IWebActionsImmutableState {
  toJS: () => IWebActionsState
}
