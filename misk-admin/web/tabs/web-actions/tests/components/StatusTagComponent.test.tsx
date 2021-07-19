import React from "react"
import { cleanup, render } from "@testing-library/react"
import { StatusTagComponent } from "../../src/components"

describe("StatusTagComponent", () => {
  afterEach(cleanup)
  it("StatusTagComponent renders empty when no status given", () => {
    const { asFragment } = render(
      <StatusTagComponent status={0} statusText={""} />
    )
    expect(asFragment()).toMatchSnapshot()
  })
  it("StatusTagComponent renders status 200 ok intent primary", () => {
    const { asFragment } = render(
      <StatusTagComponent status={200} statusText={"OK"} />
    )
    expect(asFragment()).toMatchSnapshot()
  })
  it("StatusTagComponent renders status 404 not found intent warning", () => {
    const { asFragment } = render(
      <StatusTagComponent status={404} statusText={"NOT FOUND"} />
    )
    expect(asFragment()).toMatchSnapshot()
  })
  it("StatusTagComponent renders status 500 internal error intent danger", () => {
    const { asFragment } = render(
      <StatusTagComponent status={500} statusText={"INTERNAL ERROR"} />
    )
    expect(asFragment()).toMatchSnapshot()
  })
})
