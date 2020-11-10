import { OrderedMap, OrderedSet } from "immutable"
import { get, uniqueId } from "lodash"
import {
  BaseFieldTypes,
  IActionTypes,
  IFieldTypeMetadata,
  ITypesFieldMetadata,
  ServerTypes,
  TypescriptBaseTypes
} from "."

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
