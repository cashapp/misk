import React from "react"
import { cleanup } from "react-testing-library"
import { WebActionsContainer } from "../../src/containers"
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

describe("WebActionsContainer", () => {
  afterEach(cleanup)
  it("WebActionsContainer can render with redux loading (no data)", () => {
    const { asFragment } = renderWithRedux(
      <WebActionsContainer tag={"WebActions"} />
    )
    expect(asFragment()).toMatchSnapshot()
  })
  // todo(adrw) fix
  // it("WebActionsContainer can render with redux test data", () => {
  //   const { asFragment } = renderWithRedux(
  //     <WebActionsContainer
  //       metadata={processMetadata([nonTypedActionAPI]) as IWebActionInternal[]}
  //       tag={"WebActions"}
  //     />
  //   )
  //   expect(asFragment()).toMatchSnapshot()
  // })
})
