import React from "react"
import { cleanup, render } from "react-testing-library"
import { Metadata } from "../../src/components/CommonComponents"

afterEach(cleanup)
describe("Common Components", () => {
  it("Metadata", () => {
    const { asFragment } = render(<Metadata content={"Test Content"} />)
    expect(asFragment()).toMatchSnapshot()
  })
  // it("MetadataCollapse", () => {
  //   const { asFragment } = render(<MetadataCollapse content={"Test Content"} />)
  //   expect(asFragment()).toMatchSnapshot()
  // })
})
