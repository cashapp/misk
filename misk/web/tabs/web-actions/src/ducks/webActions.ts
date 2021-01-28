import {
  createAction,
  defaultRootState,
  IAction,
  IRootState,
  ISimpleFormState,
  SimpleReduxSaga,
  simpleSelectorGet,
} from "@misk/simpleredux"
import axios from "axios"
import { HTTPMethod } from "http-method-enum"
import { OrderedMap, OrderedSet } from "immutable"
import {
  chain,
  findIndex,
  get,
  isBoolean,
  isObject,
  omit,
  padStart,
  reduce,
  size,
  uniqueId,
} from "lodash"
import { all, call, put, takeLatest } from "redux-saga/effects"

export const enum TypescriptBaseTypes {
  "any" = "any",
  "boolean" = "boolean",
  "enum" = "enum",
  "null" = "null",
  "number" = "number",
  "string" = "string",
}

export const enum ServerTypes {
  "Boolean" = "Boolean",
  "Byte" = "Byte",
  "ByteString" = "ByteString",
  "Char" = "Char",
  "Double" = "Double",
  "Enum" = "Enum",
  "Float" = "Float",
  "Int" = "Int",
  "JSON" = "JSON",
  "Long" = "Long",
  "Short" = "Short",
  "String" = "String",
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
  [ServerTypes.ByteString]: TypescriptBaseTypes.string,
  [ServerTypes.String]: TypescriptBaseTypes.string,
  [ServerTypes.Enum]: TypescriptBaseTypes.enum,
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
  allowedCapabilities: string[]
  applicationInterceptors: string[]
  httpMethod: HTTPMethod
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
  allowedCapabilities: string[]
  allowedServices: string[]
  applicationInterceptors: string[]
  authFunctionAnnotations: string[]
  httpMethod: HTTPMethod[]
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
  dirtyInput: boolean
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
  "Allowed Capabilities": "allowedCapabilities",
  "Allowed Services": "allowedServices",
  "Application Interceptor": "applicationInterceptors",
  "HTTP Method": "httpMethod",
  Function: "function",
  "Function Annotations": "functionAnnotations",
  Name: "name",
  "Network Interceptor": "networkInterceptors",
  "Parameter Types": "parameterTypes",
  "Path Pattern": "pathPattern",
  "Request Type": "requestMediaTypes",
  "Response Type": "responseMediaType",
}

/**
 * Actions
 * string enum of the defined actions that is used as type enforcement for Reducer and Sagas arguments
 */
export enum WEBACTIONS {
  ADD_REPEATED_FIELD = "WEBACTIONS_ADD_REPEATED_FIELD",
  REMOVE_REPEATED_FIELD = "WEBACTIONS_REMOVE_REPEATED_FIELD",
  METADATA = "WEBACTIONS_METADATA",
  SET_DIRTY_INPUT_FIELD = "WEBACTIONS_SET_DIRTY_INPUT_FIELD",
  UNSET_DIRTY_INPUT_FIELD = "WEBACTIONS_UNSET_DIRTY_INPUT_FIELD",
  SUCCESS = "WEBACTIONS_SUCCESS",
  FAILURE = "WEBACTIONS_FAILURE",
}

/**
 * Dispatch Object
 * Object of functions that dispatch Actions with standard defaults and any required passed in input
 * dispatch Object is used within containers to initiate any saga provided functionality
 */
export interface IWebActionsPayload {
  data?: any
  dirtyInput?: boolean
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
  webActionsSetDirtyInput: (
    id: string,
    webAction: IWebActionInternal,
    oldState: IWebActionsImmutableState
  ) => IAction<WEBACTIONS.SET_DIRTY_INPUT_FIELD, IWebActionsPayload>
  webActionsUnsetDirtyInput: (
    id: string,
    webAction: IWebActionInternal,
    oldState: IWebActionsImmutableState
  ) => IAction<WEBACTIONS.SET_DIRTY_INPUT_FIELD, IWebActionsPayload>
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
        webAction,
      }
    ),

  webActionsFailure: (error: any) =>
    createAction<WEBACTIONS.FAILURE, IWebActionsPayload>(WEBACTIONS.FAILURE, {
      ...error,
      loading: false,
      success: false,
    }),
  webActionsMetadata: () =>
    createAction<WEBACTIONS.METADATA, IWebActionsPayload>(WEBACTIONS.METADATA, {
      error: null,
      loading: true,
      success: false,
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
        webAction,
      }
    ),
  webActionsSetDirtyInput: (
    id: string,
    webAction: IWebActionInternal,
    oldState: IWebActionsImmutableState
  ) =>
    createAction<WEBACTIONS.SET_DIRTY_INPUT_FIELD, IWebActionsPayload>(
      WEBACTIONS.SET_DIRTY_INPUT_FIELD,
      {
        dirtyInput: true,
        error: null,
        id,
        loading: true,
        oldState,
        success: false,
        webAction,
      }
    ),
  webActionsSuccess: (data: any) =>
    createAction<WEBACTIONS.SUCCESS, IWebActionsPayload>(WEBACTIONS.SUCCESS, {
      ...data,
      error: null,
      loading: false,
      success: true,
    }),
  webActionsUnsetDirtyInput: (
    id: string,
    webAction: IWebActionInternal,
    oldState: IWebActionsImmutableState
  ) =>
    createAction<WEBACTIONS.SET_DIRTY_INPUT_FIELD, IWebActionsPayload>(
      WEBACTIONS.SET_DIRTY_INPUT_FIELD,
      {
        dirtyInput: false,
        error: null,
        id,
        loading: true,
        oldState,
        success: false,
        webAction,
      }
    ),
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
      return value
    case ServerTypes.Float:
      return parseFloat(value)
    case ServerTypes.Int:
      return parseInt(value, 10)
    case ServerTypes.JSON:
      return value
    case ServerTypes.Long:
      return parseInt(value, 10)
    case ServerTypes.Short:
      return parseInt(value, 10)
    default:
      return value
  }
}

/**
 * Parses for Enum server type
 * Expects type to match format
 * "Enum<qualified.class.name,enumValue1,enumValue2>"
 */
export interface IParseEnumType {
  enumClassName: string
  enumValues: string[]
}

export const parseEnumType = (serverType: string): IParseEnumType => {
  const enumType = serverType.split("<")[1].split(">")[0].split(",")
  const enumClassName = enumType[0]
  const enumValues = enumType.slice(1)
  return { enumClassName, enumValues }
}

const isInput = (data: any) =>
  isBoolean(data) ||
  (isObject(data) && size(data) > 0) ||
  (!isObject(data) && data)

export const getFieldData = (
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>,
  id: string,
  simpleForm: ISimpleFormState,
  tag: string
): any => {
  if (typesMetadata.size > 0) {
    const {
      dirtyInput,
      idChildren,
      idParent,
      name,
      repeated,
      serverType,
    } = typesMetadata.get(id)
    const parent = typesMetadata.get(idParent)
    if (id === "0" && idChildren.size === 0) {
      // root with no children
      return simpleSelectorGet(simpleForm, [`${tag}::${padId(id)}`, "data"])
    } else if (id === "0" && idChildren.size > 0) {
      // root with children, iterate over children
      const data = mapOverChildrenData(
        typesMetadata,
        idChildren,
        simpleForm,
        tag
      )
      return isInput(data) ? data : undefined
    } else if (
      parent.repeated === false &&
      repeated === false &&
      idChildren.size > 0 &&
      BaseFieldTypes.hasOwnProperty(serverType) === false
    ) {
      // field group parent node of a defined type (not a standard language type)
      const data = mapOverChildrenData(
        typesMetadata,
        idChildren,
        simpleForm,
        tag
      )
      return dirtyInput === true ? { [name]: data } : undefined
    } else if (repeated === false && idChildren.size > 0) {
      // field group parent node (standard language type)
      return mapOverChildrenData(typesMetadata, idChildren, simpleForm, tag)
    } else if (parent && parent.repeated === true && idChildren.size === 0) {
      // leaf node of a repeated list
      const data = parseType(
        serverType,
        simpleSelectorGet(simpleForm, [`${tag}::${padId(id)}`, "data"])
      )
      return dirtyInput === true ? data : undefined
    } else if (parent && parent.repeated === false && idChildren.size === 0) {
      // regular leaf node
      const data = parseType(
        serverType,
        simpleSelectorGet(simpleForm, [`${tag}::${padId(id)}`, "data"])
      )
      return dirtyInput === true ? { [name]: data } : undefined
    } else if (repeated === true && idChildren.size > 0) {
      // repeated node reached, iterate and return as list
      const data = idChildren
        .toList()
        .map((child: string) =>
          getFieldData(typesMetadata, child, simpleForm, tag)
        )
        .filter(item => isInput(item))
        .toJS()
      return dirtyInput === false ? undefined : { [name]: data }
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
          type: parentMetadata.serverType,
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
      typesMetadata: addRepeatedField(types, typesMetadata, parentId),
    }
    webActionMetadata[webActionIndex] = newWebAction
    yield put(
      dispatchWebActions.webActionsSuccess({
        metadata: webActionMetadata,
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
      typesMetadata: removeRepeatedField(childId, typesMetadata),
    }
    newWebactionMetadata[webActionIndex] = newWebAction
    yield put(
      dispatchWebActions.webActionsSuccess({
        metadata: newWebactionMetadata,
      })
    )
  } catch (e) {
    yield put(dispatchWebActions.webActionsFailure({ error: { ...e } }))
  }
}

export const recursivelySetDirtyInput = (
  id: string,
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>,
  dirtyInput: boolean
): OrderedMap<string, ITypesFieldMetadata> => {
  let newTypesMetadata = typesMetadata
  const { idChildren, idParent } = typesMetadata.get(id)
  if (dirtyInput === false) {
    idChildren.forEach(
      (child: string) =>
        (newTypesMetadata = recursivelySetDirtyInput(
          child,
          newTypesMetadata,
          dirtyInput
        ))
    )
  }
  let parent = idParent
  while (parent !== "0") {
    const {
      idChildren: parentChildren,
      idParent: newParent,
    } = newTypesMetadata.get(parent)
    const otherDirtyInputChildren = parentChildren
      .map(
        (child: string) =>
          child !== id && newTypesMetadata.get(child).dirtyInput
      )
      .has(true)
    if (dirtyInput === true || otherDirtyInputChildren === false) {
      newTypesMetadata = newTypesMetadata.setIn(
        [parent, "dirtyInput"],
        dirtyInput
      )
      parent = newParent
    } else {
      parent = "0"
    }
  }
  return newTypesMetadata.setIn([id, "dirtyInput"], dirtyInput)
}

function* handleDirtyInputField(
  action: IAction<WEBACTIONS, IWebActionsPayload>
) {
  try {
    const { dirtyInput, id, oldState, webAction } = action.payload
    const webActionIndex = findIndexAction(webAction, oldState)
    const webActionMetadata = oldState.get("metadata")
    const { typesMetadata } = webActionMetadata[webActionIndex]
    const newWebAction = {
      ...webActionMetadata[webActionIndex],
      typesMetadata: recursivelySetDirtyInput(id, typesMetadata, dirtyInput),
    }
    webActionMetadata[webActionIndex] = newWebAction
    yield put(
      dispatchWebActions.webActionsSuccess({
        metadata: webActionMetadata,
      })
    )
  } catch (e) {
    yield put(dispatchWebActions.webActionsFailure({ error: { ...e } }))
  }
}

/**
 * hash for groupBy that provides a string hash that uses an aggregate of
 * non-httpMethod metadata. This allows coalescing of web action entries
 * that only differ by HTTP method (GET, POST, PUT...)
 */
const groupByWebActionHash = (
  action: IWebActionInternal | IWebActionAPI | any
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
  typescriptType: TypescriptBaseTypes | null = null,
  dirtyInput: boolean = false
): ITypesFieldMetadata => ({
  dirtyInput,
  id,
  idChildren,
  idParent,
  name,
  repeated,
  serverType,
  typescriptType,
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
  } else if (
    BaseFieldTypes.hasOwnProperty(type) ||
    // Check if it is a complex type such as Enum<className,value1,value2>
    BaseFieldTypes.hasOwnProperty(type.split("<")[0])
  ) {
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
    } else if (
      // Handle enum type ie. Enum<className,value1,value2>
      BaseFieldTypes[type.split("<")[0]] === TypescriptBaseTypes.enum
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
            TypescriptBaseTypes.enum
          )
        )
      )
    } else {
      console.error(
        `Web Action request body field ${field} with type ${type} has no handler for the corresponding Tyepscript Type ${BaseFieldTypes[type]}`
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

const jsonTypeMetadata = OrderedMap<string, ITypesFieldMetadata>().set(
  "0",
  buildTypeFieldMetadata(
    OrderedSet(),
    "0",
    "",
    false,
    "",
    ServerTypes.JSON,
    null
  )
)

export const generateTypesMetadata = (
  action: IWebActionAPI
): OrderedMap<string, ITypesFieldMetadata> => {
  const { httpMethod, pathPattern, requestType, types } = action
  let typesMetadata = OrderedMap<string, ITypesFieldMetadata>().set(
    "0",
    buildTypeFieldMetadata(OrderedSet(), "0")
  )
  if (requestType && types && get(types, requestType)) {
    const { fields } = get(types, requestType)
    try {
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
    } catch (e) {
      if (e.toString().startsWith("RangeError")) {
        console.warn(
          `Web Action proto type is too large to parse, reverting to raw JSON input for [action = ${httpMethod} ${pathPattern}].\nTypes:`,
          types,
          "\n",
          e
        )
        return jsonTypeMetadata
      } else {
        throw e
      }
    }
    return typesMetadata
  } else if (methodHasBody(httpMethod)) {
    return jsonTypeMetadata
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
      const allowedCapabilities =
        action.allowedCapabilities && action.allowedCapabilities.length > 0
          ? action.allowedCapabilities
          : [emptyAllowedArrayValue]

      const allowedServices =
        action.allowedServices && action.allowedServices.length > 0
          ? action.allowedServices
          : [emptyAllowedArrayValue]
      // Don't include types in returned fields since they could be too large
      return {
        ...omit(action, ["types"]),
        allFields: JSON.stringify(omit(action, ["types"])),
        allowedCapabilities,
        allowedServices,
        authFunctionAnnotations,
        httpMethod: [action.httpMethod],
        function: action.function.split("fun ").pop(),
        nonAccessOrTypeFunctionAnnotations,
        typesMetadata: generateTypesMetadata(action),
      }
    })
    .groupBy(groupByWebActionHash)
    .map((actions: IWebActionInternal[]) => {
      const httpMethod = chain(actions)
        .flatMap(action => action.httpMethod)
        // remove duplicate identical HTTP method that come from
        // duplicate installation of the same webAction
        .uniq()
        .value()
      const mergedAction = actions[0]
      mergedAction.httpMethod = httpMethod.sort().reverse()
      return mergedAction
    })
    .sortBy(["name", "pathPattern"])
    .value()

function* handleMetadata() {
  const useTestData = false
  const url = useTestData
    ? "https://cashapp.github.io/misk-web/examples/data/demo/webactions.json"
    : "/api/webaction/metadata"
  try {
    const { data } = yield call(axios.get, url)
    const { webActionMetadata } = data
    const metadata = processMetadata(webActionMetadata)
    yield put(dispatchWebActions.webActionsSuccess({ metadata }))
  } catch (e) {
    yield put(dispatchWebActions.webActionsFailure({ error: { ...e } }))
  }
}

export function* watchWebActionsSagas(): SimpleReduxSaga {
  yield all([
    takeLatest(WEBACTIONS.ADD_REPEATED_FIELD, handleAddRepeatedField),
    takeLatest(WEBACTIONS.SET_DIRTY_INPUT_FIELD, handleDirtyInputField),
    takeLatest(WEBACTIONS.UNSET_DIRTY_INPUT_FIELD, handleDirtyInputField),
    takeLatest(WEBACTIONS.REMOVE_REPEATED_FIELD, handleRemoveRepeatedField),
    takeLatest(WEBACTIONS.METADATA, handleMetadata),
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
