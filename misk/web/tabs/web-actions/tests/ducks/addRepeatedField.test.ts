import HTTPMethod from "http-method-enum"
import { addRepeatedField, generateTypesMetadata } from "../../src/ducks"
import { nonTypedActionAPI, testTypes } from "../testUtilities"

describe("Add a repeated field", () => {
  it("addRepeated", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      httpMethod: HTTPMethod.POST,
      requestType: "repeatedShort",
      types: testTypes,
    })
    const parentId = typesMetadata.get("0").idChildren.first() as string
    expect(typesMetadata.get(parentId).idChildren.size).toBe(1)
    typesMetadata = addRepeatedField(testTypes, typesMetadata, parentId)
    expect(typesMetadata.get(parentId).idChildren.size).toBe(2)
    typesMetadata = addRepeatedField(testTypes, typesMetadata, parentId)
    expect(typesMetadata.get(parentId).idChildren.size).toBe(3)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("addNestedRepeated", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      httpMethod: HTTPMethod.POST,
      requestType: "repeatedNestedRepeatedShort",
      types: testTypes,
    })
    const parentId = typesMetadata.get("0").idChildren.first() as string
    expect(typesMetadata.size).toBe(6)
    expect(typesMetadata.get(parentId).idChildren.size).toBe(1)
    typesMetadata = addRepeatedField(testTypes, typesMetadata, parentId)
    expect(typesMetadata.get(parentId).idChildren.size).toBe(2)
    const nestedRepeatedId2 = typesMetadata
      .get(typesMetadata.get(parentId).idChildren.last())
      .idChildren.first() as string
    expect(typesMetadata.size).toBe(10)
    expect(typesMetadata.get(nestedRepeatedId2).idChildren.size).toBe(1)
    typesMetadata = addRepeatedField(
      testTypes,
      typesMetadata,
      nestedRepeatedId2
    )
    expect(typesMetadata.get(nestedRepeatedId2).idChildren.size).toBe(2)
    expect(typesMetadata.size).toBe(13)
    expect(typesMetadata).toMatchSnapshot()
  })
})
