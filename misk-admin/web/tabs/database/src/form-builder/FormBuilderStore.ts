import {
  get,
  isBoolean,
  isObject,
  padStart,
  reduce,
  size,
  uniqueId
} from "lodash"
import { OrderedMap, OrderedSet } from "immutable"
import {
  BaseFieldTypes,
  IActionTypes,
  IFieldTypeMetadata,
  ITypesFieldMetadata,
  ServerTypes,
  TypescriptBaseTypes
} from "../form-builder"

export const padId = (id: string) => padStart(id, 10, "0")

const parseType = (
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
  const enumType = serverType
    .split("<")[1]
    .split(">")[0]
    .split(",")
  const enumClassName = enumType[0]
  const enumValues = enumType.slice(1)
  return { enumClassName, enumValues }
}

const isInput = (data: any) =>
  isBoolean(data) ||
  (isObject(data) && size(data) > 0) ||
  (!isObject(data) && data)

const mapOverChildrenData = (
  fieldValueStore: OrderedMap<string, any>,
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>,
  childrenIds: OrderedSet<string>
) =>
  reduce(
    Object.values(
      childrenIds
        .toMap()
        .map((childId: string) =>
          getFieldData(fieldValueStore, typesMetadata, childId)
        )
        .toJS()
    ),
    (result, value) => ({ ...result, ...value }),
    {}
  )

export const getFieldData = (
  fieldValueStore: OrderedMap<string, any>,
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>,
  id: string
): any => {
  if (typesMetadata.size > 0) {
    const {
      dirtyInput,
      idChildren,
      idParent,
      name,
      repeated,
      serverType
    } = typesMetadata.get(id)
    const parent = typesMetadata.get(idParent)
    if (id === "0" && idChildren.size === 0) {
      // root with no children
      return fieldValueStore.get(id)
    } else if (id === "0" && idChildren.size > 0) {
      // root with children, iterate over children
      const data = mapOverChildrenData(
        fieldValueStore,
        typesMetadata,
        idChildren
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
        fieldValueStore,
        typesMetadata,
        idChildren
      )
      return dirtyInput === true ? { [name]: data } : undefined
    } else if (repeated === false && idChildren.size > 0) {
      // field group parent node (standard language type)
      return mapOverChildrenData(fieldValueStore, typesMetadata, idChildren)
    } else if (parent && parent.repeated === true && idChildren.size === 0) {
      // leaf node of a repeated list
      const data = parseType(serverType, fieldValueStore.get(id))
      return dirtyInput === true ? data : undefined
    } else if (parent && parent.repeated === false && idChildren.size === 0) {
      // regular leaf node
      const data = parseType(serverType, fieldValueStore.get(id))
      return dirtyInput === true ? { [name]: data } : undefined
    } else if (repeated === true && idChildren.size > 0) {
      // repeated node reached, iterate and return as list
      const data = idChildren
        .toList()
        .map((child: string) =>
          getFieldData(fieldValueStore, typesMetadata, child)
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
  fieldValueStore: OrderedMap<string, any>,
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>
) => {
  return getFieldData(fieldValueStore, typesMetadata, "0")
}

export const newFieldValueStore = () => OrderedMap<string, any>()

const buildTypeFieldMetadata = (
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
  } else if (
    BaseFieldTypes.hasOwnProperty(type) ||
    // Check if it is a complex type such as Enum<className,value1,value2>
    BaseFieldTypes.hasOwnProperty(type.split("<")[0])
  ) {
    if (
      // TODO add support date and date range picker
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
  types: IActionTypes,
  formType?: string
): OrderedMap<string, ITypesFieldMetadata> => {
  let typesMetadata = OrderedMap<string, ITypesFieldMetadata>().set(
    "0",
    buildTypeFieldMetadata(OrderedSet(), "0")
  )
  if (formType && types && get(types, formType)) {
    const { fields } = get(types, formType)
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
          `Form type is too large to parse, reverting to raw JSON input for [formType = ${formType}].\nTypes:`,
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
  } else if (formType == null) {
    return jsonTypeMetadata
  } else {
    return OrderedMap<string, ITypesFieldMetadata>()
  }
}

export const addRepeatedField = (
  types: IActionTypes,
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>,
  parentId: string
): OrderedMap<string, ITypesFieldMetadata> => {
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

const recursivelyDelete = (
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
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>,
  childId: string
): OrderedMap<string, ITypesFieldMetadata> => {
  const { idParent } = typesMetadata.get(childId)
  let newTypesMetadata = recursivelyDelete(childId, typesMetadata)
  newTypesMetadata = newTypesMetadata.setIn(
    [idParent, "idChildren"],
    newTypesMetadata.get(idParent).idChildren.delete(childId)
  )
  return newTypesMetadata
}

export const recursivelySetDirtyInput = (
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>,
  id: string,
  dirtyInput: boolean
): OrderedMap<string, ITypesFieldMetadata> => {
  let newTypesMetadata = typesMetadata
  const { idChildren, idParent } = typesMetadata.get(id)
  if (dirtyInput === false) {
    idChildren.forEach(
      (child: string) =>
        (newTypesMetadata = recursivelySetDirtyInput(
          newTypesMetadata,
          child,
          dirtyInput
        ))
    )
  }
  let parent = idParent
  while (parent !== "0") {
    const {
      idChildren: parentChildren,
      idParent: newParent
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
