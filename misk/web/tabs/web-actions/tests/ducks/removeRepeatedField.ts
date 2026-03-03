import HTTPMethod from "http-method-enum"
import {
  addRepeatedField,
  generateTypesMetadata,
  removeRepeatedField
} from "../../src/ducks"
import { nonTypedActionAPI, testTypes } from "../testUtilities"

describe("Remove a repeated field", () => {
  it("removeRepeated", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      httpMethod: HTTPMethod.POST,
      requestType: "repeatedShort",
      types: testTypes
    })
    const parentId = typesMetadata.get("0").idChildren.first() as string
    typesMetadata = addRepeatedField(testTypes, typesMetadata, parentId)
    expect(typesMetadata.size).toBe(4)
    const newChildId = typesMetadata.get(parentId).idChildren.last() as string
    expect(typesMetadata.get(newChildId)).toBeDefined()
    typesMetadata = removeRepeatedField(newChildId, typesMetadata)
    expect(typesMetadata.get(newChildId)).toBeUndefined()
    expect(typesMetadata.size).toBe(3)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("removeNestedRepeated", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      httpMethod: HTTPMethod.POST,
      requestType: "repeatedNestedRepeatedShort",
      types: testTypes
    })
    const parentId = typesMetadata.get("0").idChildren.first() as string
    expect(typesMetadata.size).toBe(6)
    typesMetadata = addRepeatedField(testTypes, typesMetadata, parentId)
    const nestedRepeatedId2 = typesMetadata
      .get(typesMetadata.get(parentId).idChildren.last())
      .idChildren.first() as string
    typesMetadata = addRepeatedField(
      testTypes,
      typesMetadata,
      nestedRepeatedId2
    )
    expect(typesMetadata.size).toBe(13)
    const newNestedChild = typesMetadata
      .get(nestedRepeatedId2)
      .idChildren.last() as string
    typesMetadata = removeRepeatedField(newNestedChild, typesMetadata)
    expect(typesMetadata.get(newNestedChild)).toBeUndefined()
    expect(typesMetadata.size).toBe(10)
    const newChild = typesMetadata.get(parentId).idChildren.last() as string
    expect(typesMetadata.get(newChild)).toBeDefined()
    typesMetadata = removeRepeatedField(newChild, typesMetadata)
    expect(typesMetadata.get(newChild)).toBeUndefined()
    expect(typesMetadata.size).toBe(6)
    expect(typesMetadata.get(nestedRepeatedId2)).toBeUndefined()
    expect(typesMetadata).toMatchSnapshot()
  })
})
