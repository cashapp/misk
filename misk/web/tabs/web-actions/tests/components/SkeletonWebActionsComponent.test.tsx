import React from "react"
import { cleanup, render } from "react-testing-library"
import { SkeletonText, SkeletonWebActionsComponent } from "../../src/components"

// TODO upstream these to @misk/core or @misk/test
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
