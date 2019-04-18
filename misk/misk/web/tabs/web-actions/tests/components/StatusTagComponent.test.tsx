import React from "react"
import { cleanup, render } from "react-testing-library"
import { StatusTagComponent } from "../../src/components"

describe("StatusTagComponent", () => {
  afterEach(cleanup)
  it("StatusTagComponent renders empty when no status given", () => {
    const { asFragment } = render(<StatusTagComponent status={[]} />)
    expect(asFragment()).toMatchSnapshot()
  })
  it("StatusTagComponent renders status 200 ok intent primary", () => {
    const { asFragment } = render(<StatusTagComponent status={["200", "ok"]} />)
    expect(asFragment()).toMatchSnapshot()
  })
  it("StatusTagComponent renders status 404 not found intent warning", () => {
    const { asFragment } = render(
      <StatusTagComponent status={["404", "not found"]} />
    )
    expect(asFragment()).toMatchSnapshot()
  })
  it("StatusTagComponent renders status 500 internal error intent danger", () => {
    const { asFragment } = render(
      <StatusTagComponent status={["500", "internal error"]} />
    )
    expect(asFragment()).toMatchSnapshot()
  })
})
