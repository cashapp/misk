import React from "react"
import { UL } from "@blueprintjs/core"
import WebActionCollapse from "./WebActionCollapse"

interface Props {
  description: String
}

export default function WebActionDescription({ description }: Props) {
  if (description) {
    return (
      <WebActionCollapse title={"Description"} doubleWidth={true}>
        <UL style={{ listStyle: "none" }}>
          <li>{description}</li>
        </UL>
      </WebActionCollapse>
    )
  } else {
    return null
  }
}
