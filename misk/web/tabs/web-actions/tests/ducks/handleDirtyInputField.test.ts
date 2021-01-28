import HTTPMethod from "http-method-enum"
import {
  generateTypesMetadata,
  recursivelySetDirtyInput,
} from "../../src/ducks"
import { nonTypedActionAPI, testTypes } from "../testUtilities"

describe("Set dirty input for a field", () => {
  it("Set dirty input for a field", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      httpMethod: HTTPMethod.POST,
      requestType: "repeatedShort",
      types: testTypes,
    })
    const parentId = typesMetadata.get("0").idChildren.first() as string
    expect(typesMetadata.get(parentId).idChildren.size).toBe(1)
    const fieldId = typesMetadata.get(parentId).idChildren.first() as string
    expect(typesMetadata.get(fieldId).dirtyInput).toBeFalsy()
    typesMetadata = recursivelySetDirtyInput(fieldId, typesMetadata, true)
    expect(typesMetadata.get(fieldId).dirtyInput).toBeTruthy()
    typesMetadata = recursivelySetDirtyInput(fieldId, typesMetadata, false)
    expect(typesMetadata.get(fieldId).dirtyInput).toBeFalsy()
    expect(typesMetadata).toMatchSnapshot()
  })
  it("Set dirty input for a nested repeated field", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      httpMethod: HTTPMethod.POST,
      requestType: "repeatedNestedRepeatedShort",
      types: testTypes,
    })
    const parentId = typesMetadata.get("0").idChildren.first() as string
    expect(typesMetadata.get(parentId).idChildren.size).toBe(1)
    const nestedRepeatedId = typesMetadata
      .get(typesMetadata.get(parentId).idChildren.first())
      .idChildren.first() as string
    const repeatedId = typesMetadata
      .get(nestedRepeatedId)
      .idChildren.first() as string
    const fieldId = typesMetadata.get(repeatedId).idChildren.first() as string
    expect(typesMetadata.get(fieldId).dirtyInput).toBeFalsy()
    typesMetadata = recursivelySetDirtyInput(fieldId, typesMetadata, true)
    expect(typesMetadata.get(fieldId).dirtyInput).toBeTruthy()
    expect(typesMetadata.get(repeatedId).dirtyInput).toBeTruthy()
    expect(typesMetadata.get(nestedRepeatedId).dirtyInput).toBeTruthy()
    typesMetadata = recursivelySetDirtyInput(fieldId, typesMetadata, false)
    expect(typesMetadata.get(fieldId).dirtyInput).toBeFalsy()
    expect(typesMetadata.get(repeatedId).dirtyInput).toBeFalsy()
    expect(typesMetadata.get(nestedRepeatedId).dirtyInput).toBeFalsy()
    typesMetadata = recursivelySetDirtyInput(
      nestedRepeatedId,
      typesMetadata,
      true
    )
    expect(typesMetadata.get(fieldId).dirtyInput).toBeFalsy()
    expect(typesMetadata.get(repeatedId).dirtyInput).toBeFalsy()
    expect(typesMetadata.get(nestedRepeatedId).dirtyInput).toBeTruthy()
    expect(typesMetadata).toMatchSnapshot()
  })
})
