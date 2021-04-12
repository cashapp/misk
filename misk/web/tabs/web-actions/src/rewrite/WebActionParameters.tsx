import React from "react";
import { Card, H5 } from "@blueprintjs/core"

interface Props {
  parameters: string[]
}

export default function WebActionParameters({ parameters }: Props) {

  if (parameters.length == 0) {
    return null;
  }

  return(
    <Card style={{marginTop: "12px", padding: "12px"}}>
      <H5> Request Parameters </H5>
      <ol style={{ listStyle: "none", marginBottom: "0px"}}>
        {parameters.map(parameter => (
          <li>{parameter}</li>
        ))}
      </ol>
    </Card>
  )
}