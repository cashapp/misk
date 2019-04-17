import React from "react"
import { cleanup } from "react-testing-library"
import { WebActionsContainer } from "../../src/containers"
import { processMetadata } from "../../src/ducks"
import { nonTypedActionAPI } from "../testUtilities"
import { renderWithRedux } from "../upstreamableTestUtilities"

describe("WebActionsContainer", () => {
  afterEach(cleanup)
  it("WebActionsContainer can render with redux loading (no data)", () => {
    const { asFragment } = renderWithRedux(
      <WebActionsContainer tag={"WebActions"} />
    )
    expect(asFragment()).toMatchSnapshot()
  })
  it("processMetadata converts IWebActionAPI[] to IWebActionInternal[]", () => {
    expect(processMetadata([nonTypedActionAPI])).toMatchSnapshot()
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
