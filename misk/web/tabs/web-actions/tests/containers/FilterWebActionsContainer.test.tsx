import React from "react"
import { cleanup } from "@testing-library/react"
import { FilterWebActionsContainer } from "../../src/containers"
import { renderWithRedux } from "../upstreamableTestUtilities"

describe("FilterWebActionsContainer", () => {
  afterEach(cleanup)
  it("FilterWebActionsContainer can render with redux", () => {
    const { asFragment } = renderWithRedux(
      <FilterWebActionsContainer tag={"FilterTag"} />
    )
    expect(asFragment()).toMatchSnapshot()
  })
})
