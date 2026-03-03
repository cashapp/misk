import { IconNames } from "@blueprintjs/icons"
import React from "react"
import { cleanup, render } from "@testing-library/react"
import { QuantityButton } from "../../src/components"
import { dispatchWebActions } from "../../src/ducks"
import { nonTypedActionInternal } from "../testUtilities"

describe("Quantity Button", () => {
  afterEach(cleanup)
  it("QuantityButton for Add Field", () => {
    const { asFragment } = render(
      <QuantityButton
        action={nonTypedActionInternal}
        changeFn={dispatchWebActions.webActionsAdd}
        content={"Add Field"}
        icon={IconNames.PLUS}
        id={"0"}
        oldState={{}}
      />
    )
    expect(asFragment()).toMatchSnapshot()
  })
  it("QuantityButton for Remove Field", () => {
    const { asFragment } = render(
      <QuantityButton
        action={nonTypedActionInternal}
        changeFn={dispatchWebActions.webActionsRemove}
        content={"Remove Field"}
        icon={IconNames.CROSS}
        id={"0"}
        oldState={{}}
      />
    )
    expect(asFragment()).toMatchSnapshot()
  })
})
