import React from "react"
import { cleanup } from "@testing-library/react"
import { WebActionsContainer } from "../../src/containers"
import { renderWithRedux } from "../upstreamableTestUtilities"

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
