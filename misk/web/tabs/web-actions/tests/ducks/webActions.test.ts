import HTTPMethod from "http-method-enum"
import {
  addRepeatedField,
  generateTypesMetadata,
  getFieldData,
  IWebActionAPI,
  padId,
  parseType,
  removeRepeatedField,
  ServerTypes,
  TypescriptBaseTypes
} from "../../src/ducks"

/**
 * Constants
 */
const testTypes = {
  nestedNoRepeatedInt: {
    fields: [
      {
        name: "Nested Int Field",
        repeated: false,
        type: "noRepeatedInt"
      }
    ]
  },
  nestedRepeatedDouble: {
    fields: [
      {
        name: "Nested Double Field",
        repeated: false,
        type: "repeatedDouble"
      }
    ]
  },
  noRepeatedInt: {
    fields: [
      {
        name: "Int Field",
        repeated: false,
        type: "Int"
      }
    ]
  },
  repeatedDouble: {
    fields: [
      {
        name: "Repeated Double Field",
        repeated: true,
        type: "Double"
      }
    ]
  },
  repeatedNestedNoRepeatedInt: {
    fields: [
      {
        name: "Repeated Nested Int Field",
        repeated: true,
        type: "nestedNoRepeatedInt"
      }
    ]
  },
  repeatedNestedRepeatedDouble: {
    fields: [
      {
        name: "Nested Repeated Double Field",
        repeated: true,
        type: "nestedRepeatedDouble"
      }
    ]
  }
}

const simpleForm = {
  simpleTag: "simpleForm"
}

/**
 * Tests
 */
describe("Test parsing of string to ServerType: parseType()", () => {
  it("double", () => {
    expect(parseType(ServerTypes.Double, "-123.456")).toEqual(-123.456)
  })
  it("int", () => {
    expect(parseType(ServerTypes.Int, "-123.456")).toEqual(-123)
  })
  it("long", () => {
    expect(parseType(ServerTypes.Long, "-123.456")).toEqual(-123.456)
  })
  it("short", () => {
    expect(parseType(ServerTypes.Short, "-123.456")).toEqual(-123.456)
  })
})

/**
 * Other TODOs
 * Have request body and response be tag drop downs so they can be hidden
 * Only show 2 columns on requests with
 */

const nonTypedActionAPI: IWebActionAPI = {
  allowedRoles: [] as string[],
  allowedServices: [] as string[],
  applicationInterceptors: [] as string[],
  dispatchMechanism: HTTPMethod.GET,
  function:
    "fun misk.web.actions.LivenessCheckAction.livenessCheck(): misk.web.Response<kotlin.String>",
  functionAnnotations: [
    "@misk.web.Get(pathPattern=/_liveness)",
    "@misk.web.ResponseContentType(value=text/plain;charset=utf-8)",
    "@misk.security.authz.Unauthenticated()"
  ],
  name: "LivenessCheckAction",
  networkInterceptors: [
    "misk.web.interceptors.InternalErrorInterceptorFactory$Companion$INTERCEPTOR$1",
    "misk.web.interceptors.RequestLogContextInterceptor",
    "misk.web.interceptors.RequestLoggingInterceptor",
    "misk.web.interceptors.MetricsInterceptor",
    "misk.web.interceptors.TracingInterceptor",
    "misk.web.exceptions.ExceptionHandlingInterceptor",
    "misk.web.interceptors.MarshallerInterceptor",
    "misk.web.interceptors.WideOpenDevelopmentInterceptor"
  ],
  parameterTypes: [] as string[],
  pathPattern: "/_liveness",
  requestMediaTypes: ["*/*"],
  responseMediaType: "text/plain;charset=utf-8",
  returnType: "misk.web.Response<kotlin.String>"
}

describe("Build typesMetadata from a raw WebActionMetadata: generateTypesMetadata()", () => {
  it("generateNonTypedGET", () => {
    const typesMetadata = generateTypesMetadata(nonTypedActionAPI)
    expect(typesMetadata).toBeDefined()
    expect(typesMetadata.size).toBeDefined()
    expect(typesMetadata.size).toBe(0)
    expect(typesMetadata.get("0")).toBeUndefined()
    expect(typesMetadata).toMatchSnapshot()
  })
  it("generateNonTypedPOST", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST
    })
    expect(typesMetadata).toBeDefined()
    expect(typesMetadata.size).toBeDefined()
    expect(typesMetadata.size).toBe(1)
    const tmRoot = typesMetadata.get("0")
    expect(tmRoot).toBeDefined()
    expect(tmRoot.idChildren.size).toBe(0)
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
    expect(typesMetadata).toBeDefined()
    expect(typesMetadata.size).toBeDefined()
    expect(typesMetadata.size).toBe(2)
    const tmRoot = typesMetadata.get("0")
    expect(tmRoot).toBeDefined()
    expect(tmRoot.idChildren.size).toBe(1)
    expect(tmRoot.idChildren.first()).toBeDefined()
    const tmField = typesMetadata.get(tmRoot.idChildren.first())
    expect(tmField).toBeDefined()
    expect(tmField.idChildren.size).toBe(0)
    expect(tmField.idParent).toBe(tmRoot.id)
    expect(tmField.repeated).toBe(false)
    expect(tmField.serverType).toBe(ServerTypes.Int)
    expect(tmField.typescriptType).toBe(TypescriptBaseTypes.number)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("generateNested", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "nestedNoRepeatedInt",
      types: testTypes
    })
    expect(typesMetadata).toBeDefined()
    expect(typesMetadata.size).toBeDefined()
    expect(typesMetadata.size).toBe(3)
    const tmRoot = typesMetadata.get("0")
    expect(tmRoot).toBeDefined()
    expect(tmRoot.idChildren.size).toBe(1)
    expect(tmRoot.idChildren.first()).toBeDefined()
    const tmFieldGroup = typesMetadata.get(tmRoot.idChildren.first())
    expect(tmFieldGroup).toBeDefined()
    expect(tmFieldGroup.idChildren.size).toBe(1)
    expect(tmFieldGroup.idParent).toBe(tmRoot.id)
    expect(tmFieldGroup.repeated).toBe(false)
    expect(tmFieldGroup.serverType).toBe("noRepeatedInt")
    expect(tmFieldGroup.typescriptType).toBeNull()
    const tmField = typesMetadata.get(tmFieldGroup.idChildren.first())
    expect(tmField).toBeDefined()
    expect(tmField.idChildren.size).toBe(0)
    expect(tmField.idParent).toBe(tmFieldGroup.id)
    expect(tmField.repeated).toBe(false)
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
    expect(typesMetadata).toBeDefined()
    expect(typesMetadata.size).toBeDefined()
    expect(typesMetadata.size).toBe(3)
    const tmRoot = typesMetadata.get("0")
    expect(tmRoot).toBeDefined()
    expect(tmRoot.idChildren.size).toBe(1)
    expect(tmRoot.idChildren.first()).toBeDefined()
    const tmRepeatedParent = typesMetadata.get(tmRoot.idChildren.first())
    expect(tmRepeatedParent).toBeDefined()
    expect(tmRepeatedParent.idChildren.size).toBe(1)
    expect(tmRepeatedParent.idParent).toBe(tmRoot.id)
    expect(tmRepeatedParent.repeated).toBe(true)
    expect(tmRepeatedParent.serverType).toBe(ServerTypes.Double)
    expect(tmRepeatedParent.typescriptType).toBeNull()
    const tmRepeatedChild = typesMetadata.get(
      tmRepeatedParent.idChildren.first()
    )
    expect(tmRepeatedChild).toBeDefined()
    expect(tmRepeatedChild.idChildren.size).toBe(0)
    expect(tmRepeatedChild.idParent).toBe(tmRepeatedParent.id)
    expect(tmRepeatedChild.repeated).toBe(false)
    expect(tmRepeatedChild.serverType).toBe(ServerTypes.Double)
    expect(tmRepeatedChild.typescriptType).toBe(TypescriptBaseTypes.number)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("generateRepeatedNested", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedNestedNoRepeatedInt",
      types: testTypes
    })
    expect(typesMetadata).toBeDefined()
    expect(typesMetadata.size).toBeDefined()
    expect(typesMetadata.size).toBe(5)
    const tmRoot = typesMetadata.get("0")
    expect(tmRoot).toBeDefined()
    expect(tmRoot.idChildren.size).toBe(1)
    expect(tmRoot.idChildren.first()).toBeDefined()
    const tmRepeatedParent = typesMetadata.get(tmRoot.idChildren.first())
    expect(tmRepeatedParent).toBeDefined()
    expect(tmRepeatedParent.idChildren.size).toBe(1)
    expect(tmRepeatedParent.idParent).toBe(tmRoot.id)
    expect(tmRepeatedParent.repeated).toBe(true)
    expect(tmRepeatedParent.serverType).toBe("nestedNoRepeatedInt")
    expect(tmRepeatedParent.typescriptType).toBeNull()
    const tmRepeatedChild = typesMetadata.get(
      tmRepeatedParent.idChildren.first()
    )
    expect(tmRepeatedChild).toBeDefined()
    expect(tmRepeatedChild.idChildren.size).toBe(1)
    expect(tmRepeatedChild.idParent).toBe(tmRepeatedParent.id)
    expect(tmRepeatedChild.repeated).toBe(false)
    expect(tmRepeatedChild.serverType).toBe("nestedNoRepeatedInt")
    expect(tmRepeatedChild.typescriptType).toBeNull()
    const tmFieldGroup = typesMetadata.get(tmRepeatedChild.idChildren.first())
    expect(tmFieldGroup).toBeDefined()
    expect(tmFieldGroup.idChildren.size).toBe(1)
    expect(tmFieldGroup.idParent).toBe(tmRepeatedChild.id)
    expect(tmFieldGroup.repeated).toBe(false)
    expect(tmFieldGroup.serverType).toBe("noRepeatedInt")
    expect(tmFieldGroup.typescriptType).toBeNull()
    const tmField = typesMetadata.get(tmFieldGroup.idChildren.first())
    expect(tmField).toBeDefined()
    expect(tmField.idChildren.size).toBe(0)
    expect(tmField.idParent).toBe(tmFieldGroup.id)
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
    expect(typesMetadata).toBeDefined()
    expect(typesMetadata.size).toBeDefined()
    expect(typesMetadata.size).toBe(6)
    const tmRoot = typesMetadata.get("0")
    expect(tmRoot).toBeDefined()
    expect(tmRoot.idChildren.size).toBe(1)
    expect(tmRoot.idChildren.first()).toBeDefined()
    const tmRepeatedParent = typesMetadata.get(tmRoot.idChildren.first())
    expect(tmRepeatedParent).toBeDefined()
    expect(tmRepeatedParent.idChildren.size).toBe(1)
    expect(tmRepeatedParent.idParent).toBe(tmRoot.id)
    expect(tmRepeatedParent.repeated).toBe(true)
    expect(tmRepeatedParent.serverType).toBe("nestedRepeatedDouble")
    expect(tmRepeatedParent.typescriptType).toBeNull()
    const tmRepeatedChild = typesMetadata.get(
      tmRepeatedParent.idChildren.first()
    )
    expect(tmRepeatedChild).toBeDefined()
    expect(tmRepeatedChild.idChildren.size).toBe(1)
    expect(tmRepeatedChild.idParent).toBe(tmRepeatedParent.id)
    expect(tmRepeatedChild.repeated).toBe(false)
    expect(tmRepeatedChild.serverType).toBe("nestedRepeatedDouble")
    expect(tmRepeatedChild.typescriptType).toBeNull()
    const tmFieldGroup = typesMetadata.get(tmRepeatedChild.idChildren.first())
    expect(tmFieldGroup).toBeDefined()
    expect(tmFieldGroup.idChildren.size).toBe(1)
    expect(tmFieldGroup.idParent).toBe(tmRepeatedChild.id)
    expect(tmFieldGroup.repeated).toBe(false)
    expect(tmFieldGroup.serverType).toBe("repeatedDouble")
    expect(tmFieldGroup.typescriptType).toBeNull()
    const tmFieldRepeatedParent = typesMetadata.get(
      tmFieldGroup.idChildren.first()
    )
    expect(tmFieldRepeatedParent).toBeDefined()
    expect(tmFieldRepeatedParent.idChildren.size).toBe(1)
    expect(tmFieldRepeatedParent.idParent).toBe(tmFieldGroup.id)
    expect(tmFieldRepeatedParent.repeated).toBe(true)
    expect(tmFieldRepeatedParent.serverType).toBe(ServerTypes.Double)
    expect(tmFieldRepeatedParent.typescriptType).toBeNull()
    const tmFieldRepeatedChild = typesMetadata.get(
      tmFieldRepeatedParent.idChildren.first()
    )
    expect(tmFieldRepeatedChild).toBeDefined()
    expect(tmFieldRepeatedChild.idChildren.size).toBe(0)
    expect(tmFieldRepeatedChild.idParent).toBe(tmFieldRepeatedParent.id)
    expect(tmFieldRepeatedChild.repeated).toBe(false)
    expect(tmFieldRepeatedChild.serverType).toBe(ServerTypes.Double)
    expect(tmFieldRepeatedChild.typescriptType).toBe(TypescriptBaseTypes.number)
    expect(typesMetadata).toMatchSnapshot()
  })
})

describe("Get formatted form data: getFieldData()", () => {
  it("getNonTypedGET", () => {
    const typesMetadata = generateTypesMetadata(nonTypedActionAPI)
    expect(typesMetadata).toBeDefined()
    const data = getFieldData(typesMetadata, "0", simpleForm, "Tag")
    expect(data).toBeUndefined()
  })
  it("getNonTypedPOST", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST
    })
    expect(typesMetadata).toBeDefined()
    const data = "Alpha"
    const getData = getFieldData(
      typesMetadata,
      "0",
      { ...simpleForm, [`Tag::${padId("0")}`]: { data } },
      "Tag"
    )
    expect(getData).toBeDefined()
    expect(getData).toBe(data)
    expect(getData).toMatchSnapshot()
  })
  it("getNoNested", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "noRepeatedInt",
      types: testTypes
    })
    expect(typesMetadata).toBeDefined()
    const data = "123456"
    const fieldId = typesMetadata.get("0").idChildren.first() as string
    const { name: fieldName } = typesMetadata.get(fieldId)
    const getData = getFieldData(
      typesMetadata,
      "0",
      { ...simpleForm, [`Tag::${padId(fieldId)}`]: { data } },
      "Tag"
    )
    expect(getData).toBeDefined()
    expect(getData).toEqual({ [fieldName]: 123456 })
    expect(getData).toMatchSnapshot()
  })
  it("getNested", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "nestedNoRepeatedInt",
      types: testTypes
    })
    expect(typesMetadata).toBeDefined()
    const data = "123456"
    const fieldGroupId = typesMetadata.get("0").idChildren.first() as string
    const { name: fieldGroupName } = typesMetadata.get(fieldGroupId)
    const fieldId = typesMetadata.get(fieldGroupId).idChildren.first() as string
    const { name: fieldName } = typesMetadata.get(fieldId)
    const getData = getFieldData(
      typesMetadata,
      "0",
      { ...simpleForm, [`Tag::${padId(fieldId)}`]: { data } },
      "Tag"
    )
    expect(getData).toBeDefined()
    expect(getData).toEqual({ [fieldGroupName]: { [fieldName]: 123456 } })
    expect(getData).toMatchSnapshot()
  })
  it("getRepeated", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedDouble",
      types: testTypes
    })
    expect(typesMetadata).toBeDefined()
    const repeatedParentId = typesMetadata.get("0").idChildren.first() as string
    typesMetadata = addRepeatedField(testTypes, typesMetadata, repeatedParentId)
    const data1 = "-123.456"
    const data2 = "456.789"
    const fieldId1 = typesMetadata
      .get(repeatedParentId)
      .idChildren.first() as string
    const fieldId2 = typesMetadata
      .get(repeatedParentId)
      .idChildren.last() as string
    const { name: fieldName } = typesMetadata.get(fieldId1)
    const getData = getFieldData(
      typesMetadata,
      "0",
      {
        ...simpleForm,
        [`Tag::${padId(fieldId1)}`]: { data: data1 },
        [`Tag::${padId(fieldId2)}`]: { data: data2 }
      },
      "Tag"
    )
    expect(getData).toBeDefined()
    expect(getData).toEqual({ [fieldName]: [-123.456, 456.789] })
    expect(getData).toMatchSnapshot()
  })
  it("getRepeatedNested", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedNestedNoRepeatedInt",
      types: testTypes
    })
    expect(typesMetadata).toBeDefined()
    const repeatedParentId = typesMetadata.get("0").idChildren.first() as string
    const { name: nestedRepeatedName } = typesMetadata.get(repeatedParentId)
    typesMetadata = addRepeatedField(testTypes, typesMetadata, repeatedParentId)
    const data1 = "-123456"
    const data2 = "456789"
    const { name: repeatedName } = typesMetadata.get(
      typesMetadata
        .get(typesMetadata.get(repeatedParentId).idChildren.first())
        .idChildren.first()
    )
    const fieldId1 = typesMetadata
      .get(
        typesMetadata
          .get(typesMetadata.get(repeatedParentId).idChildren.first())
          .idChildren.first()
      )
      .idChildren.first() as string
    const fieldId2 = typesMetadata
      .get(
        typesMetadata
          .get(typesMetadata.get(repeatedParentId).idChildren.last())
          .idChildren.first()
      )
      .idChildren.first() as string
    const { name: fieldName } = typesMetadata.get(fieldId1)
    const getData = getFieldData(
      typesMetadata,
      "0",
      {
        ...simpleForm,
        [`Tag::${padId(fieldId1)}`]: { data: data1 },
        [`Tag::${padId(fieldId2)}`]: { data: data2 }
      },
      "Tag"
    )
    expect(getData).toBeDefined()
    expect(getData).toEqual({
      [nestedRepeatedName]: [
        { [repeatedName]: { [fieldName]: -123456 } },
        { [repeatedName]: { [fieldName]: 456789 } }
      ]
    })
    expect(getData).toMatchSnapshot()
  })
  it("getRepeatedNestedRepeated", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedNestedRepeatedDouble",
      types: testTypes
    })
    expect(typesMetadata).toBeDefined()
    const repeatedParentId = typesMetadata.get("0").idChildren.first() as string
    const { name: nestedRepeatedName } = typesMetadata.get(repeatedParentId)
    typesMetadata = addRepeatedField(testTypes, typesMetadata, repeatedParentId)
    const data1 = "-123.456"
    const data2 = "456.789"
    const data3 = "-789.123"
    const { name: repeatedName } = typesMetadata.get(
      typesMetadata
        .get(typesMetadata.get(repeatedParentId).idChildren.first())
        .idChildren.first()
    )
    const fieldId1 = typesMetadata
      .get(
        typesMetadata
          .get(
            typesMetadata
              .get(typesMetadata.get(repeatedParentId).idChildren.first())
              .idChildren.first()
          )
          .idChildren.first()
      )
      .idChildren.first() as string
    const fieldId2Parent = typesMetadata
      .get(
        typesMetadata
          .get(typesMetadata.get(repeatedParentId).idChildren.last())
          .idChildren.first()
      )
      .idChildren.first() as string
    typesMetadata = addRepeatedField(testTypes, typesMetadata, fieldId2Parent)
    const fieldId2 = typesMetadata
      .get(fieldId2Parent)
      .idChildren.first() as string
    const fieldId3 = typesMetadata
      .get(fieldId2Parent)
      .idChildren.last() as string
    const { name: fieldName } = typesMetadata.get(fieldId1)
    const getData = getFieldData(
      typesMetadata,
      "0",
      {
        ...simpleForm,
        [`Tag::${padId(fieldId1)}`]: { data: data1 },
        [`Tag::${padId(fieldId2)}`]: { data: data2 },
        [`Tag::${padId(fieldId3)}`]: { data: data3 }
      },
      "Tag"
    )
    expect(getData).toBeDefined()
    expect(getData).toEqual({
      [nestedRepeatedName]: [
        { [repeatedName]: { [fieldName]: [-123.456] } },
        { [repeatedName]: { [fieldName]: [456.789, -789.123] } }
      ]
    })
    expect(getData).toMatchSnapshot()
  })
})

describe("Add a repeated field: addRepeatedField()", () => {
  it("addRepeated", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedDouble",
      types: testTypes
    })
    const parentId = typesMetadata.get("0").idChildren.first() as string
    expect(typesMetadata).toBeDefined()
    expect(typesMetadata.size).toBe(3)
    expect(typesMetadata.get(parentId).idChildren.size).toBe(1)
    typesMetadata = addRepeatedField(testTypes, typesMetadata, parentId)
    expect(typesMetadata.size).toBe(4)
    expect(typesMetadata.get(parentId).idChildren.size).toBe(2)
    typesMetadata = addRepeatedField(testTypes, typesMetadata, parentId)
    expect(typesMetadata.size).toBe(5)
    expect(typesMetadata.get(parentId).idChildren.size).toBe(3)
    const tmFirstChild = typesMetadata.get(
      typesMetadata.get(parentId).idChildren.first()
    )
    typesMetadata.get(parentId).idChildren.map((child: string) => {
      const tmChild = typesMetadata.get(child)
      expect(tmChild.idParent).toBe(parentId)
      expect(tmChild.name).toBe(tmFirstChild.name)
      expect(tmChild.repeated).toBe(false)
      expect(tmChild.serverType).toBe(tmFirstChild.serverType)
      expect(tmChild.typescriptType).toBe(tmFirstChild.typescriptType)
    })
    expect(typesMetadata).toMatchSnapshot()
  })
  it("addNestedRepeated", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedNestedRepeatedDouble",
      types: testTypes
    })
    const parentId = typesMetadata.get("0").idChildren.first() as string
    const nestedRepeatedId1 = typesMetadata
      .get(typesMetadata.get(parentId).idChildren.first())
      .idChildren.first() as string
    expect(typesMetadata).toBeDefined()
    expect(typesMetadata.size).toBe(6)
    expect(typesMetadata.get(parentId).idChildren.size).toBe(1)
    expect(typesMetadata.get(nestedRepeatedId1).idChildren.size).toBe(1)
    typesMetadata = addRepeatedField(testTypes, typesMetadata, parentId)
    const nestedRepeatedId2 = typesMetadata
      .get(typesMetadata.get(parentId).idChildren.last())
      .idChildren.first() as string
    expect(typesMetadata.size).toBe(10)
    expect(typesMetadata.get(parentId).idChildren.size).toBe(2)
    expect(typesMetadata.get(nestedRepeatedId1).idChildren.size).toBe(1)
    expect(typesMetadata.get(nestedRepeatedId1).typescriptType).toBeNull()
    expect(
      typesMetadata.get(typesMetadata.get(nestedRepeatedId1).idChildren.first())
        .repeated
    ).toBe(true)
    expect(
      typesMetadata.get(
        typesMetadata
          .get(typesMetadata.get(nestedRepeatedId1).idChildren.first())
          .idChildren.first()
      ).typescriptType
    ).toBe(TypescriptBaseTypes.number)
    expect(typesMetadata.get(nestedRepeatedId2).idChildren.size).toBe(1)
    expect(typesMetadata.get(nestedRepeatedId2).typescriptType).toBeNull()
    expect(
      typesMetadata.get(typesMetadata.get(nestedRepeatedId1).idChildren.first())
        .repeated
    ).toBe(true)
    expect(
      typesMetadata.get(
        typesMetadata
          .get(typesMetadata.get(nestedRepeatedId1).idChildren.first())
          .idChildren.first()
      ).typescriptType
    ).toBe(TypescriptBaseTypes.number)
    typesMetadata = addRepeatedField(
      testTypes,
      typesMetadata,
      nestedRepeatedId2
    )
    expect(typesMetadata.size).toBe(13)
    expect(typesMetadata.get(parentId).idChildren.size).toBe(2)
    expect(typesMetadata.get(nestedRepeatedId1).idChildren.size).toBe(1)
    expect(typesMetadata.get(nestedRepeatedId2).idChildren.size).toBe(2)
    expect(typesMetadata).toMatchSnapshot()
  })
})

describe("Remove a repeated field: removeRepeatedField()", () => {
  it("removeRepeated", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedDouble",
      types: testTypes
    })
    const parentId = typesMetadata.get("0").idChildren.first() as string
    expect(typesMetadata).toBeDefined()
    typesMetadata = addRepeatedField(testTypes, typesMetadata, parentId)
    expect(typesMetadata.size).toBe(4)
    expect(typesMetadata.get(parentId).idChildren.size).toBe(2)
    const newChildId = typesMetadata.get(parentId).idChildren.last() as string
    expect(typesMetadata.get(newChildId)).toBeDefined()
    typesMetadata = removeRepeatedField(newChildId, typesMetadata)
    expect(typesMetadata.get(newChildId)).toBeUndefined()
    expect(typesMetadata.size).toBe(3)
    expect(typesMetadata.get(parentId).idChildren.size).toBe(1)
    expect(typesMetadata).toMatchSnapshot()
  })
  it("removeNestedRepeated", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedNestedRepeatedDouble",
      types: testTypes
    })
    const parentId = typesMetadata.get("0").idChildren.first() as string
    const nestedRepeatedId1 = typesMetadata
      .get(typesMetadata.get(parentId).idChildren.first())
      .idChildren.first() as string
    expect(typesMetadata).toBeDefined()
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
    expect(typesMetadata.get(parentId).idChildren.size).toBe(2)
    expect(typesMetadata.get(nestedRepeatedId1).idChildren.size).toBe(1)
    expect(typesMetadata.get(nestedRepeatedId2).idChildren.size).toBe(2)
    const newNestedChild = typesMetadata
      .get(nestedRepeatedId2)
      .idChildren.last() as string
    expect(typesMetadata.get(newNestedChild)).toBeDefined()
    typesMetadata = removeRepeatedField(newNestedChild, typesMetadata)
    expect(typesMetadata.get(newNestedChild)).toBeUndefined()
    expect(typesMetadata.size).toBe(10)
    expect(typesMetadata.get(parentId).idChildren.size).toBe(2)
    expect(typesMetadata.get(nestedRepeatedId1).idChildren.size).toBe(1)
    expect(typesMetadata.get(nestedRepeatedId2).idChildren.size).toBe(1)
    const newChild = typesMetadata.get(parentId).idChildren.last() as string
    expect(typesMetadata.get(newChild)).toBeDefined()
    typesMetadata = removeRepeatedField(newChild, typesMetadata)
    expect(typesMetadata.get(newChild)).toBeUndefined()
    expect(typesMetadata.size).toBe(6)
    expect(typesMetadata.get(parentId).idChildren.size).toBe(1)
    expect(typesMetadata.get(nestedRepeatedId2)).toBeUndefined()
    expect(typesMetadata).toMatchSnapshot()
  })
})
