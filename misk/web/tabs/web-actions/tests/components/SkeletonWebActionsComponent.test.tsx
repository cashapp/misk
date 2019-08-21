import React from "react"
import { cleanup, render } from "@testing-library/react"
import { SkeletonText, SkeletonWebActionsComponent } from "../../src/components"

describe("SkeletonWebActionsComponent", () => {
  afterEach(cleanup)
  it("SkeletonText glowing text block used as loading screen", () => {
    const { asFragment } = render(<SkeletonText />)
    expect(asFragment()).toMatchSnapshot()
  })
  it("SkeletonWebActionsComponent single Web Actions Card used as loading screen", () => {
    const { asFragment } = render(<SkeletonWebActionsComponent />)
    expect(asFragment()).toMatchSnapshot()
  })
})
