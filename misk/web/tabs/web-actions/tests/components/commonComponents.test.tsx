import React from "react"
import { cleanup, render } from "@testing-library/react"
import { Metadata, MetadataCollapse } from "../../src/components"
import { renderWithRedux } from "../upstreamableTestUtilities"

describe("Common Components", () => {
  afterEach(cleanup)
  it("Metadata", () => {
    const { asFragment } = render(<Metadata content={"Test Content"} />)
    expect(asFragment()).toMatchSnapshot()
  })
  it("MetadataCollapse can render with redux", () => {
    const { asFragment } = renderWithRedux(
      <MetadataCollapse content={"Test Content"} tag={"TestTag"}>
        <span>{"Collapse Span"}</span>
      </MetadataCollapse>
    )
    expect(asFragment()).toMatchSnapshot()
  })
})
