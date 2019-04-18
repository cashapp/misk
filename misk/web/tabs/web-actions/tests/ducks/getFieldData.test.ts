import HTTPMethod from "http-method-enum"
import {
  addRepeatedField,
  generateTypesMetadata,
  getFieldData,
  padId
} from "../../src/ducks"
import { nonTypedActionAPI, simpleForm, testTypes } from "../testUtilities"

describe("Get formatted form data", () => {
  it("get non-typed GET", () => {
    const typesMetadata = generateTypesMetadata(nonTypedActionAPI)
    const data = getFieldData(typesMetadata, "0", simpleForm, "Tag")
    expect(data).toBeUndefined()
  })
  it("get non-typed POST", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST
    })
    const data = "Alpha"
    const getData = getFieldData(
      typesMetadata,
      "0",
      { ...simpleForm, [`Tag::${padId("0")}`]: { data } },
      "Tag"
    )
    expect(getData).toBe(data)
    expect(getData).toMatchSnapshot()
  })
  it("get non-repeated int", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "noRepeatedInt",
      types: testTypes
    })
    const data = "123456"
    const fieldId = typesMetadata.get("0").idChildren.first() as string
    const getData = getFieldData(
      typesMetadata,
      "0",
      { ...simpleForm, [`Tag::${padId(fieldId)}`]: { data } },
      "Tag"
    )
    expect(getData).toMatchSnapshot()
  })
  it("get nested int", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "nestedNoRepeatedInt",
      types: testTypes
    })
    const data = "123456"
    const fieldGroupId = typesMetadata.get("0").idChildren.first() as string
    const fieldId = typesMetadata.get(fieldGroupId).idChildren.first() as string
    const getData = getFieldData(
      typesMetadata,
      "0",
      { ...simpleForm, [`Tag::${padId(fieldId)}`]: { data } },
      "Tag"
    )
    expect(getData).toMatchSnapshot()
  })
  it("get repeated double", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedDouble",
      types: testTypes
    })
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
    expect(getData).toMatchSnapshot()
  })
  it("get repeated nested int", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedNestedNoRepeatedInt",
      types: testTypes
    })
    const repeatedParentId = typesMetadata.get("0").idChildren.first() as string
    typesMetadata = addRepeatedField(testTypes, typesMetadata, repeatedParentId)
    const data1 = "-123456"
    const data2 = "456789"
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
    expect(getData).toMatchSnapshot()
  })
  it("get repeated nested repeated double", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedNestedRepeatedDouble",
      types: testTypes
    })
    const repeatedParentId = typesMetadata.get("0").idChildren.first() as string
    typesMetadata = addRepeatedField(testTypes, typesMetadata, repeatedParentId)
    const data1 = "-123.456"
    const data2 = "456.789"
    const data3 = "-789.123"
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
    expect(getData).toMatchSnapshot()
  })
  it("get data but no field input", () => {
    let typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedNestedRepeatedDouble",
      types: testTypes
    })
    const repeatedParentId = typesMetadata.get("0").idChildren.first() as string
    typesMetadata = addRepeatedField(testTypes, typesMetadata, repeatedParentId)
    const fieldId2Parent = typesMetadata
      .get(
        typesMetadata
          .get(typesMetadata.get(repeatedParentId).idChildren.last())
          .idChildren.first()
      )
      .idChildren.first() as string
    typesMetadata = addRepeatedField(testTypes, typesMetadata, fieldId2Parent)
    const getData = getFieldData(
      typesMetadata,
      "0",
      {
        ...simpleForm
      },
      "Tag"
    )
    expect(getData).toMatchSnapshot()
  })
})
