import {
  createAction,
  defaultRootState,
  IAction,
  IRootState,
  ISimpleFormState,
  simpleSelect
} from "@misk/simpleredux"
import axios from "axios"
import { HTTPMethod } from "http-method-enum"
import { OrderedMap, OrderedSet } from "immutable"
import { chain, findIndex, get, padStart, reduce, uniqueId } from "lodash"
import { all, AllEffect, call, put, takeLatest } from "redux-saga/effects"

export const enum TypescriptBaseTypes {
  "any" = "any",
  "boolean" = "boolean",
  "enum" = "enum",
  "null" = "null",
  "number" = "number",
  "string" = "string"
}

export const enum ServerTypes {
  "Boolean" = "Boolean",
  "Byte" = "Byte",
  "ByteString" = "ByteString",
  "Char" = "Char",
  "Double" = "Double",
  "Int" = "Int",
  "JSON" = "JSON",
  "Long" = "Long",
  "Short" = "Short",
  "String" = "String"
}

export interface IBaseFieldTypes {
  [serverType: string]: TypescriptBaseTypes
}

export const BaseFieldTypes: IBaseFieldTypes = {
  [ServerTypes.Boolean]: TypescriptBaseTypes.boolean,
  [ServerTypes.Short]: TypescriptBaseTypes.number,
  [ServerTypes.Int]: TypescriptBaseTypes.number,
  [ServerTypes.JSON]: TypescriptBaseTypes.string,
  [ServerTypes.Long]: TypescriptBaseTypes.number,
  [ServerTypes.Double]: TypescriptBaseTypes.number,
  [ServerTypes.ByteString]: TypescriptBaseTypes.string,
  [ServerTypes.String]: TypescriptBaseTypes.string
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
  requestType?: string
  types?: IActionTypes
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
  typesMetadata: { [key: string]: ITypesFieldMetadata }
}

export interface ITypesFieldMetadata {
  idParent: string
  idChildren: OrderedSet<string>
  id: string
  name: string
  repeated: boolean
  serverType: ServerTypes | null
  typescriptType: TypescriptBaseTypes | null
}

export interface ITypesMetadata {
  [key: string]: ITypesFieldMetadata
}

export const findIndexAction = (
  action: IWebActionInternal,
  webActionsRaw: IWebActionsImmutableState
): number =>
  findIndex(
    webActionsRaw.get("metadata"),
    (rawAction: IWebActionInternal) => rawAction.allFields === action.allFields
  )

export const methodHasBody = (method: HTTPMethod) =>
  method === HTTPMethod.PATCH ||
  method === HTTPMethod.POST ||
  method === HTTPMethod.PUT

export const padId = (id: string) => padStart(id, 10, "0")

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
  ADD_REPEATED_FIELD = "WEBACTIONS_ADD_REPEATED_FIELD",
  REMOVE_REPEATED_FIELD = "WEBACTIONS_REMOVE_REPEATED_FIELD",
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
  oldState?: IWebActionsImmutableState
  id?: string
  success: boolean
  webAction?: IWebActionInternal
}

export interface IDispatchWebActions {
  webActionsAdd: (
    id: string,
    webAction: IWebActionInternal,
    oldState: IWebActionsImmutableState
  ) => IAction<WEBACTIONS.ADD_REPEATED_FIELD, IWebActionsPayload>
  webActionsFailure: (
    error: any
  ) => IAction<WEBACTIONS.FAILURE, IWebActionsPayload>
  webActionsMetadata: () => IAction<WEBACTIONS.METADATA, IWebActionsPayload>
  webActionsRemove: (
    id: string,
    webAction: IWebActionInternal,
    oldState: IWebActionsImmutableState
  ) => IAction<WEBACTIONS.REMOVE_REPEATED_FIELD, IWebActionsPayload>
  webActionsSuccess: (
    data: any
  ) => IAction<WEBACTIONS.SUCCESS, IWebActionsPayload>
}

export const dispatchWebActions: IDispatchWebActions = {
  webActionsAdd: (
    id: string,
    webAction: IWebActionInternal,
    oldState: IWebActionsImmutableState
  ) =>
    createAction<WEBACTIONS.ADD_REPEATED_FIELD, IWebActionsPayload>(
      WEBACTIONS.ADD_REPEATED_FIELD,
      {
        error: null,
        id,
        loading: true,
        oldState,
        success: false,
        webAction
      }
    ),
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
  webActionsRemove: (
    id: string,
    webAction: IWebActionInternal,
    oldState: IWebActionsImmutableState
  ) =>
    createAction<WEBACTIONS.REMOVE_REPEATED_FIELD, IWebActionsPayload>(
      WEBACTIONS.REMOVE_REPEATED_FIELD,
      {
        error: null,
        id,
        loading: true,
        oldState,
        success: false,
        webAction
      }
    ),
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

export const mapOverChildrenData = (
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>,
  children: OrderedSet<string>,
  simpleForm: ISimpleFormState,
  tag: string
) =>
  reduce(
    Object.values(
      children
        .toMap()
        .map((child: string) =>
          getFieldData(typesMetadata, child, simpleForm, tag)
        )
        .toJS()
    ),
    (result, value) => ({ ...result, ...value }),
    {}
  )

export const parseType = (
  serverType: ServerTypes,
  value: string
): boolean | number | string => {
  switch (serverType) {
    case ServerTypes.Boolean:
      return value
    case ServerTypes.Byte:
      return value
    case ServerTypes.ByteString:
      return value
    case ServerTypes.Char:
      return value
    case ServerTypes.Double:
      return parseFloat(value)
    case ServerTypes.Int:
      return parseInt(value, 10)
    case ServerTypes.JSON:
      return value
    case ServerTypes.Long:
      return parseInt(value, 10)
    case ServerTypes.Short:
      return parseFloat(value)
    default:
      return value
  }
}

export const getFieldData = (
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>,
  id: string,
  simpleForm: ISimpleFormState,
  tag: string
): any => {
  if (typesMetadata.size > 0) {
    const {
      idChildren,
      idParent,
      name,
      repeated,
      serverType
    } = typesMetadata.get(id)
    const parent = typesMetadata.get(idParent)
    if (id === "0" && idChildren.size === 0) {
      // root with no children
      return simpleSelect(simpleForm, `${tag}::${padId(id)}`, "data")
    } else if (id === "0" && idChildren.size > 0) {
      // root with children, iterate over children
      return mapOverChildrenData(typesMetadata, idChildren, simpleForm, tag)
    } else if (
      parent.repeated === false &&
      repeated === false &&
      idChildren.size > 0 &&
      BaseFieldTypes.hasOwnProperty(serverType) === false
    ) {
      // field group parent node of a defined type (not a standard language type)
      return {
        [name]: mapOverChildrenData(typesMetadata, idChildren, simpleForm, tag)
      }
    } else if (repeated === false && idChildren.size > 0) {
      // field group parent node (standard language type)
      return mapOverChildrenData(typesMetadata, idChildren, simpleForm, tag)
    } else if (parent && parent.repeated === true && idChildren.size === 0) {
      // leaf node of a repeated list
      return parseType(
        serverType,
        simpleSelect(simpleForm, `${tag}::${padId(id)}`, "data")
      )
    } else if (parent && parent.repeated === false && idChildren.size === 0) {
      // regular leaf node
      return {
        [name]: parseType(
          serverType,
          simpleSelect(simpleForm, `${tag}::${padId(id)}`, "data")
        )
      }
    } else if (repeated === true && idChildren.size > 0) {
      // repeated node reached, iterate and return as list
      return {
        [name]: idChildren
          .toList()
          .map((child: string) =>
            getFieldData(typesMetadata, child, simpleForm, tag)
          )
          .toJS()
      }
    } else {
      throw new Error("Unhandled field data retrieval case.")
    }
  } else {
    return
  }
}

export const getFormData = (
  webAction: IWebActionInternal,
  simpleForm: ISimpleFormState,
  tag: string,
  webActionsRaw: IWebActionsImmutableState
) => {
  const webActionIndex = findIndexAction(webAction, webActionsRaw)
  const webActionMetadata = webActionsRaw.get("metadata")
  const { typesMetadata } = webActionMetadata[webActionIndex]
  const data = getFieldData(typesMetadata, "0", simpleForm, tag)
  return data
}

export const addRepeatedField = (
  types: IActionTypes,
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>,
  parentId: string
) => {
  let newTypesMetadata = typesMetadata as OrderedMap<
    string,
    ITypesFieldMetadata
  >
  const parentMetadata = newTypesMetadata.get(parentId)
  const newChildId = uniqueId()
  const parentChildren = parentMetadata.idChildren.add(newChildId)
  newTypesMetadata = newTypesMetadata
    .setIn([parentId, "idChildren"], parentChildren)
    .mergeDeep(
      generateFieldTypesMetadata(
        {
          name: parentMetadata.name,
          repeated: false,
          type: parentMetadata.serverType
        },
        types,
        newTypesMetadata,
        newChildId,
        parentId
      )
    )
  return newTypesMetadata
}

function* handleAddRepeatedField(
  action: IAction<WEBACTIONS, IWebActionsPayload>
) {
  try {
    const { oldState, id: parentId, webAction } = action.payload
    const webActionIndex = findIndexAction(webAction, oldState)
    const webActionMetadata = oldState.get("metadata")
    const { types, typesMetadata } = webActionMetadata[webActionIndex]
    const newWebAction = {
      ...webActionMetadata[webActionIndex],
      typesMetadata: addRepeatedField(types, typesMetadata, parentId)
    }
    webActionMetadata[webActionIndex] = newWebAction
    yield put(
      dispatchWebActions.webActionsSuccess({
        metadata: webActionMetadata
      })
    )
  } catch (e) {
    yield put(dispatchWebActions.webActionsFailure({ error: { ...e } }))
  }
}

export const recursivelyDelete = (
  id: string,
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>
): OrderedMap<string, ITypesFieldMetadata> => {
  let newTypesMetadata = typesMetadata
  const { idParent } = typesMetadata.get(id)
  typesMetadata
    .get(id)
    .idChildren.forEach(
      (child: string) =>
        (newTypesMetadata = recursivelyDelete(child, newTypesMetadata))
    )
  return newTypesMetadata
    .setIn(
      [idParent, "idChildren"],
      newTypesMetadata.getIn([idParent, "idChildren"]).delete(id)
    )
    .delete(id)
}

export const removeRepeatedField = (
  childId: string,
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>
) => {
  const { idParent } = typesMetadata.get(childId)
  let newTypesMetadata = recursivelyDelete(childId, typesMetadata)
  newTypesMetadata = newTypesMetadata.setIn(
    [idParent, "idChildren"],
    newTypesMetadata.get(idParent).idChildren.delete(childId)
  )
  return newTypesMetadata
}

function* handleRemoveRepeatedField(
  action: IAction<WEBACTIONS, IWebActionsPayload>
) {
  try {
    const { oldState, id: childId, webAction } = action.payload
    const webActionIndex = findIndexAction(webAction, oldState)
    const webActionMetadata = oldState.get("metadata")
    const { typesMetadata } = webActionMetadata[webActionIndex]
    const newWebactionMetadata = webActionMetadata
    const newWebAction = {
      ...newWebactionMetadata[webActionIndex],
      typesMetadata: removeRepeatedField(childId, typesMetadata)
    }
    newWebactionMetadata[webActionIndex] = newWebAction
    yield put(
      dispatchWebActions.webActionsSuccess({
        metadata: newWebactionMetadata
      })
    )
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

export const buildTypeFieldMetadata = (
  idChildren: OrderedSet<string> = OrderedSet(),
  id: string = "",
  name: string = "",
  repeated: boolean = false,
  idParent: string = "0",
  serverType: ServerTypes | null = null,
  typescriptType: TypescriptBaseTypes | null = null
): ITypesFieldMetadata => ({
  id,
  idChildren,
  idParent,
  name,
  repeated,
  serverType,
  typescriptType
})

const generateFieldTypesMetadata = (
  field: IFieldTypeMetadata,
  types: IActionTypes,
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>,
  id: string = uniqueId(),
  parent: string = ""
): OrderedMap<string, ITypesFieldMetadata> => {
  const { name, repeated, type } = field
  if (repeated) {
    const repeatedChildId = uniqueId()
    return typesMetadata
      .set(
        id,
        buildTypeFieldMetadata(
          OrderedSet().add(repeatedChildId),
          id,
          name,
          true,
          parent,
          type
        )
      )
      .mergeDeep(
        generateFieldTypesMetadata(
          { ...field, repeated: false },
          types,
          typesMetadata,
          repeatedChildId,
          id
        )
      )
  } else if (BaseFieldTypes.hasOwnProperty(type)) {
    if (
      BaseFieldTypes[type] === TypescriptBaseTypes.boolean ||
      BaseFieldTypes[type] === TypescriptBaseTypes.number ||
      BaseFieldTypes[type] === TypescriptBaseTypes.string
    ) {
      return typesMetadata.mergeDeep(
        OrderedMap<string, ITypesFieldMetadata>().set(
          id,
          buildTypeFieldMetadata(
            OrderedSet(),
            id,
            name,
            repeated,
            parent,
            type,
            BaseFieldTypes[type]
          )
        )
      )
    } else {
      console.log(
        `Valid Base Field Type ${type} has no handler for the corresponding Tyepscript Type ${
          BaseFieldTypes[type]
        }`
      )
      return typesMetadata
    }
  } else if (types.hasOwnProperty(type)) {
    const fields = types[type].fields
    let childIds = OrderedSet()
    let subMap = typesMetadata
    for (const subField in fields) {
      if (fields.hasOwnProperty(subField)) {
        const childId = uniqueId()
        childIds = childIds.add(childId)
        subMap = subMap.mergeDeep(
          generateFieldTypesMetadata(
            fields[subField],
            types,
            typesMetadata,
            childId,
            id
          )
        )
      }
    }
    return typesMetadata
      .set(
        id,
        buildTypeFieldMetadata(
          childIds,
          id,
          name,
          repeated,
          parent,
          type,
          BaseFieldTypes[type]
        )
      )
      .mergeDeep(subMap)
  } else {
    return typesMetadata.set(
      id,
      buildTypeFieldMetadata(
        OrderedSet(),
        id,
        name,
        repeated,
        parent,
        type,
        BaseFieldTypes[type]
      )
    )
  }
}

export const generateTypesMetadata = (
  action: IWebActionAPI
): OrderedMap<string, ITypesFieldMetadata> => {
  const { dispatchMechanism, requestType, types } = action
  let typesMetadata = OrderedMap<string, ITypesFieldMetadata>().set(
    "0",
    buildTypeFieldMetadata(OrderedSet(), "0")
  )
  if (requestType && types && get(types, requestType)) {
    const { fields } = get(types, requestType)
    for (const field in fields) {
      if (fields.hasOwnProperty(field)) {
        const id = uniqueId()
        typesMetadata = typesMetadata.mergeDeep(
          generateFieldTypesMetadata(
            fields[field],
            types,
            typesMetadata,
            id,
            "0"
          )
        )
        typesMetadata = typesMetadata.setIn(
          ["0", "idChildren"],
          typesMetadata.getIn(["0", "idChildren"]).add(id)
        )
      }
    }
    return typesMetadata
  } else if (methodHasBody(dispatchMechanism)) {
    return OrderedMap<string, ITypesFieldMetadata>().set(
      "0",
      buildTypeFieldMetadata(
        OrderedSet(),
        "0",
        "",
        false,
        "",
        ServerTypes.JSON,
        TypescriptBaseTypes.string
      )
    )
  } else {
    return OrderedMap<string, ITypesFieldMetadata>()
  }
}

export const processMetadata = (webActionMetadata: IWebActionAPI[]): any =>
  chain(webActionMetadata)
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
        action.allowedRoles && action.allowedRoles.length > 0
          ? action.allowedRoles.join(", ")
          : emptyAllowedArrayValue

      const allowedServices =
        action.allowedServices && action.allowedServices.length > 0
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
        nonAccessOrTypeFunctionAnnotations,
        typesMetadata: generateTypesMetadata(action)
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

function* handleMetadata() {
  try {
    const { data } = yield call(axios.get, "/api/webaction/metadata")
    const { webActionMetadata } = data
    const metadata = processMetadata(webActionMetadata)
    yield put(dispatchWebActions.webActionsSuccess({ metadata }))
  } catch (e) {
    yield put(dispatchWebActions.webActionsFailure({ error: { ...e } }))
  }
}

export function* watchWebActionsSagas(): IterableIterator<AllEffect> {
  yield all([
    takeLatest(WEBACTIONS.ADD_REPEATED_FIELD, handleAddRepeatedField),
    takeLatest(WEBACTIONS.REMOVE_REPEATED_FIELD, handleRemoveRepeatedField),
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
    case WEBACTIONS.ADD_REPEATED_FIELD:
    case WEBACTIONS.REMOVE_REPEATED_FIELD:
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
  metadata: IWebActionInternal[]
  [key: string]: any
}

export interface IWebActionsImmutableState extends OrderedMap<string, any> {
  toJS: () => IWebActionsState
}
