import React from "react"
import { Card, H5, HTMLTable } from "@blueprintjs/core"
import { ParameterMetaData } from "./types"
import WebActionParameter from "./WebActionParameter"

interface Props {
  parameters: ParameterMetaData[]
}

export default function WebActionParameters({ parameters }: Props) {
  if (parameters.length == 0) {
    return null
  }

  return (
    <Card style={{ marginTop: "12px", padding: "12px" }}>
      <H5> Request Parameters </H5>
      <HTMLTable style={{marginBottom: "0px"}}>
        {parameters.map(parameter => (
          <WebActionParameter parameter={parameter} />
        ))}
      </HTMLTable>
    </Card>
  )
}
