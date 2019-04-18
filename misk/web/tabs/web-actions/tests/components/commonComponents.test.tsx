import React from "react"
import { cleanup, render } from "react-testing-library"
import { Metadata, MetadataCollapse, MetadataMenu } from "../../src/components"
import { renderWithRedux } from "../upstreamableTestUtilities"

describe("Common Components", () => {
  afterEach(cleanup)
  it("MetadataMenu", () => {
    const { asFragment } = render(<MetadataMenu />)
    expect(asFragment()).toMatchSnapshot()
  })
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
