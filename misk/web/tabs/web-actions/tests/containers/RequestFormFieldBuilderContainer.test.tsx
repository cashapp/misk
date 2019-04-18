import HTTPMethod from "http-method-enum"
import React from "react"
import { cleanup } from "react-testing-library"
import { RequestFormFieldBuilderContainer } from "../../src/containers"
import { generateTypesMetadata } from "../../src/ducks"
import {
  nonTypedActionAPI,
  nonTypedActionInternal,
  testTypes
} from "../testUtilities"
import { renderWithRedux } from "../upstreamableTestUtilities"

jest.mock("@misk/core", () => {
  const miskCore = require.requireActual("@misk/core")
  const mock = {
    ...miskCore,
    Column: jest.fn((props: { children: any }) => (
      <div className={"Column"}>{props.children}</div>
    )),
    FlexContainer: jest.fn((props: { children: any }) => (
      <div className={"FlexContainer"}>{props.children}</div>
    ))
  }
  return mock
})

describe("RequestFormFieldBuilderContainer", () => {
  afterEach(cleanup)
  it("Doesn't fail on empty metadata", () => {
    const typesMetadata = generateTypesMetadata(nonTypedActionAPI)
    const { asFragment } = renderWithRedux(
      <RequestFormFieldBuilderContainer
        action={nonTypedActionInternal}
        id={"0"}
        tag={"WebActions"}
        typesMetadata={typesMetadata}
      />
    )
    expect(asFragment()).toMatchSnapshot()
  })
  it("Renders single body input", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST
    })
    const { asFragment } = renderWithRedux(
      <RequestFormFieldBuilderContainer
        action={nonTypedActionInternal}
        id={"0"}
        tag={"WebActions"}
        typesMetadata={typesMetadata}
      />
    )
    expect(asFragment()).toMatchSnapshot()
  })
  it("Renders typed form int field", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "noRepeatedInt",
      types: testTypes
    })
    const { asFragment } = renderWithRedux(
      <RequestFormFieldBuilderContainer
        action={nonTypedActionInternal}
        id={"0"}
        tag={"WebActions"}
        typesMetadata={typesMetadata}
      />
    )
    expect(asFragment()).toMatchSnapshot()
  })
  it("Renders typed form nested int field", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "nestedNoRepeatedInt",
      types: testTypes
    })
    const { asFragment } = renderWithRedux(
      <RequestFormFieldBuilderContainer
        action={nonTypedActionInternal}
        id={"0"}
        tag={"WebActions"}
        typesMetadata={typesMetadata}
      />
    )
    expect(asFragment()).toMatchSnapshot()
  })
  it("Renders typed form repeated short field", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedShort",
      types: testTypes
    })
    const { asFragment } = renderWithRedux(
      <RequestFormFieldBuilderContainer
        action={nonTypedActionInternal}
        id={"0"}
        tag={"WebActions"}
        typesMetadata={typesMetadata}
      />
    )
    expect(asFragment()).toMatchSnapshot()
  })
  it("Renders typed form repeated nested int field", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedNestedNoRepeatedInt",
      types: testTypes
    })
    const { asFragment } = renderWithRedux(
      <RequestFormFieldBuilderContainer
        action={nonTypedActionInternal}
        id={"0"}
        tag={"WebActions"}
        typesMetadata={typesMetadata}
      />
    )
    expect(asFragment()).toMatchSnapshot()
  })
  it("Renders typed form repeated nested repeated short field", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "repeatedNestedRepeatedShort",
      types: testTypes
    })
    const { asFragment } = renderWithRedux(
      <RequestFormFieldBuilderContainer
        action={nonTypedActionInternal}
        id={"0"}
        tag={"WebActions"}
        typesMetadata={typesMetadata}
      />
    )
    expect(asFragment()).toMatchSnapshot()
  })
  it("Renders typed form multiple top level fields", () => {
    const typesMetadata = generateTypesMetadata({
      ...nonTypedActionAPI,
      dispatchMechanism: HTTPMethod.POST,
      requestType: "multipleFlatFields",
      types: testTypes
    })
    const { asFragment } = renderWithRedux(
      <RequestFormFieldBuilderContainer
        action={nonTypedActionInternal}
        id={"0"}
        tag={"WebActions"}
        typesMetadata={typesMetadata}
      />
    )
    expect(asFragment()).toMatchSnapshot()
  })
})
