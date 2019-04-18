import HTTPMethod from "http-method-enum"
import {
  generateTypesMetadata,
  ServerTypes,
  TypescriptBaseTypes
} from "../../src/ducks"
import { nonTypedActionAPI, testTypes } from "../testUtilities"

describe("Build typesMetadata from a raw WebActionMetadata", () => {
  it("generateNonTypedGET", () => {
    const typesMetadata = generateTypesMetadata(nonTypedActionAPI)
    expect(typesMetadata.size).toBe(0)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("generateNonTypedPOST", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST
    })
    expect(typesMetadata.size).toBe(1)
    const tmRoot = typesMetadata.get("0")
    expect(tmRoot.serverType).toBe(ServerTypes.JSON)
    expect(tmRoot.typescriptType).toBe(TypescriptBaseTypes.string)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("generateNoNested", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "noRepeatedInt",
      types: testTypes
    })
    expect(typesMetadata.size).toBe(2)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("generateNested", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "nestedNoRepeatedInt",
      types: testTypes
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
  it("generateRepeated", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedDouble",
      types: testTypes
    })
    expect(typesMetadata.size).toBe(3)
    const tmRoot = typesMetadata.get("0")
    expect(tmRoot.idChildren.size).toBe(1)
    const tmParent = typesMetadata.get(tmRoot.idChildren.first())
    expect(tmParent.repeated).toBe(true)
    expect(tmParent.serverType).toBe(ServerTypes.Double)
    expect(tmParent.typescriptType).toBeNull()
    const tmChild = typesMetadata.get(tmParent.idChildren.first())
    expect(tmChild.repeated).toBe(false)
    expect(tmChild.serverType).toBe(ServerTypes.Double)
    expect(tmChild.typescriptType).toBe(TypescriptBaseTypes.number)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("generateRepeatedNested", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedNestedNoRepeatedInt",
      types: testTypes
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
  it("generateRepeatedNestedRepeated", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedNestedRepeatedDouble",
      types: testTypes
    })
    expect(typesMetadata.size).toBe(6)
    const tmRoot = typesMetadata.get("0")
    expect(tmRoot.idChildren.size).toBe(1)
    const tmParent = typesMetadata.get(tmRoot.idChildren.first())
    expect(tmParent.repeated).toBe(true)
    expect(tmParent.serverType).toBe("nestedRepeatedDouble")
    expect(tmParent.typescriptType).toBeNull()
    const tmChild = typesMetadata.get(tmParent.idChildren.first())
    expect(tmChild.repeated).toBe(false)
    expect(tmChild.serverType).toBe("nestedRepeatedDouble")
    expect(tmChild.typescriptType).toBeNull()
    const tmFieldGroup = typesMetadata.get(tmChild.idChildren.first())
    expect(tmFieldGroup.repeated).toBe(false)
    expect(tmFieldGroup.serverType).toBe("repeatedDouble")
    expect(tmFieldGroup.typescriptType).toBeNull()
    const tmFieldParent = typesMetadata.get(tmFieldGroup.idChildren.first())
    expect(tmFieldParent.repeated).toBe(true)
    expect(tmFieldParent.serverType).toBe(ServerTypes.Double)
    expect(tmFieldParent.typescriptType).toBeNull()
    const tmFieldChild = typesMetadata.get(tmFieldParent.idChildren.first())
    expect(tmFieldChild.repeated).toBe(false)
    expect(tmFieldChild.serverType).toBe(ServerTypes.Double)
    expect(tmFieldChild.typescriptType).toBe(TypescriptBaseTypes.number)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("generate multiple top level fields", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "multipleFlatFields",
      types: testTypes
    })
    expect(typesMetadata.size).toBe(5)
    expect(typesMetadata).toMatchSnapshot()
  })
})
