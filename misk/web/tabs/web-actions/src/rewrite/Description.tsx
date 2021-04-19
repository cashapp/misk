import React from "react"
import { Card, H5 } from "@blueprintjs/core"

interface Props {
  description: String
}

export default function Description({ description }: Props) {
  if (description) {
    return (
      <Card style={{ marginTop: "12px", padding: "12px" }}>
        <H5> Description </H5>
        {description}
      </Card>
    )
  } else {
    return null
  }
}
