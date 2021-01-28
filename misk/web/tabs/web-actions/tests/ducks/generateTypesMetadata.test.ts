import HTTPMethod from "http-method-enum"
import {
  generateTypesMetadata,
  ServerTypes,
  TypescriptBaseTypes,
} from "../../src/ducks"
import { nonTypedActionAPI, testTypes } from "../testUtilities"

describe("Build typesMetadata from a raw WebActionMetadata", () => {
  it("get non-typed GET", () => {
    const typesMetadata = generateTypesMetadata(nonTypedActionAPI)
    expect(typesMetadata.size).toBe(0)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("get non-typed POST", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      httpMethod: HTTPMethod.POST,
    })
    expect(typesMetadata.size).toBe(1)
    const tmRoot = typesMetadata.get("0")
    expect(tmRoot.serverType).toBe(ServerTypes.JSON)
    expect(tmRoot.typescriptType).toBeNull()
    expect(typesMetadata).toMatchSnapshot()
  })
  it("get non-repeated int", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      httpMethod: HTTPMethod.POST,
      requestType: "noRepeatedInt",
      types: testTypes,
    })
    expect(typesMetadata.size).toBe(2)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("get nested int", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      httpMethod: HTTPMethod.POST,
      requestType: "nestedNoRepeatedInt",
      types: testTypes,
    })
    expect(typesMetadata.size).toBe(3)
    const tmRoot = typesMetadata.get("0")
    const tmFieldGroup = typesMetadata.get(tmRoot.idChildren.first())
    expect(tmFieldGroup.serverType).toBe("noRepeatedInt")
    expect(tmFieldGroup.typescriptType).toBeNull()
    const tmField = typesMetadata.get(tmFieldGroup.idChildren.first())
    expect(tmField.serverType).toBe(ServerTypes.Int)
    expect(tmField.typescriptType).toBe(TypescriptBaseTypes.number)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("get repeated short", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      httpMethod: HTTPMethod.POST,
      requestType: "repeatedShort",
      types: testTypes,
    })
    expect(typesMetadata.size).toBe(3)
    const tmRoot = typesMetadata.get("0")
    expect(tmRoot.idChildren.size).toBe(1)
    const tmParent = typesMetadata.get(tmRoot.idChildren.first())
    expect(tmParent.repeated).toBe(true)
    expect(tmParent.serverType).toBe(ServerTypes.Short)
    expect(tmParent.typescriptType).toBeNull()
    const tmChild = typesMetadata.get(tmParent.idChildren.first())
    expect(tmChild.repeated).toBe(false)
    expect(tmChild.serverType).toBe(ServerTypes.Short)
    expect(tmChild.typescriptType).toBe(TypescriptBaseTypes.number)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("get repeated nested int", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      httpMethod: HTTPMethod.POST,
      requestType: "repeatedNestedNoRepeatedInt",
      types: testTypes,
    })
    expect(typesMetadata.size).toBe(5)
    const tmRoot = typesMetadata.get("0")
    expect(tmRoot.idChildren.size).toBe(1)
    const tmParent = typesMetadata.get(tmRoot.idChildren.first())
    expect(tmParent.repeated).toBe(true)
    expect(tmParent.serverType).toBe("nestedNoRepeatedInt")
    expect(tmParent.typescriptType).toBeNull()
    const tmChild = typesMetadata.get(tmParent.idChildren.first())
    expect(tmChild.repeated).toBe(false)
    expect(tmChild.serverType).toBe("nestedNoRepeatedInt")
    expect(tmChild.typescriptType).toBeNull()
    const tmFieldGroup = typesMetadata.get(tmChild.idChildren.first())
    expect(tmFieldGroup.repeated).toBe(false)
    expect(tmFieldGroup.serverType).toBe("noRepeatedInt")
    expect(tmFieldGroup.typescriptType).toBeNull()
    const tmField = typesMetadata.get(tmFieldGroup.idChildren.first())
    expect(tmField.repeated).toBe(false)
    expect(tmField.serverType).toBe(ServerTypes.Int)
    expect(tmField.typescriptType).toBe(TypescriptBaseTypes.number)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("get repeated nested repeated short", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      httpMethod: HTTPMethod.POST,
      requestType: "repeatedNestedRepeatedShort",
      types: testTypes,
    })
    expect(typesMetadata.size).toBe(6)
    const tmRoot = typesMetadata.get("0")
    expect(tmRoot.idChildren.size).toBe(1)
    const tmParent = typesMetadata.get(tmRoot.idChildren.first())
    expect(tmParent.repeated).toBe(true)
    expect(tmParent.serverType).toBe("nestedRepeatedShort")
    expect(tmParent.typescriptType).toBeNull()
    const tmChild = typesMetadata.get(tmParent.idChildren.first())
    expect(tmChild.repeated).toBe(false)
    expect(tmChild.serverType).toBe("nestedRepeatedShort")
    expect(tmChild.typescriptType).toBeNull()
    const tmFieldGroup = typesMetadata.get(tmChild.idChildren.first())
    expect(tmFieldGroup.repeated).toBe(false)
    expect(tmFieldGroup.serverType).toBe("repeatedShort")
    expect(tmFieldGroup.typescriptType).toBeNull()
    const tmFieldParent = typesMetadata.get(tmFieldGroup.idChildren.first())
    expect(tmFieldParent.repeated).toBe(true)
    expect(tmFieldParent.serverType).toBe(ServerTypes.Short)
    expect(tmFieldParent.typescriptType).toBeNull()
    const tmFieldChild = typesMetadata.get(tmFieldParent.idChildren.first())
    expect(tmFieldChild.repeated).toBe(false)
    expect(tmFieldChild.serverType).toBe(ServerTypes.Short)
    expect(tmFieldChild.typescriptType).toBe(TypescriptBaseTypes.number)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("generate multiple top level fields", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      httpMethod: HTTPMethod.POST,
      requestType: "multipleFlatFields",
      types: testTypes,
    })
    expect(typesMetadata.size).toBe(5)
    expect(typesMetadata).toMatchSnapshot()
  })
})
