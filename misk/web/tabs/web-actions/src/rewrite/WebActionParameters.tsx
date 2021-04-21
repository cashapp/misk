import React from "react"
import { HTMLTable, UL } from "@blueprintjs/core"
import { ParameterMetaData } from "./types"
import WebActionParameter from "./WebActionParameter"
import WebActionCollapse from "./WebActionCollapse";

interface Props {
  parameters: ParameterMetaData[]
}

export default function WebActionParameters({ parameters }: Props) {

  if (parameters.length == 0) {
    return null
  }

  return (
    <WebActionCollapse
      title={"Request Parameters"}
      doubleWidth={true}>
      <UL style={{listStyle: "none"}}>
        <li>
          <HTMLTable style={{marginBottom: "0px"}}>
            {parameters.map(parameter => (
              <WebActionParameter parameter={parameter} />
            ))}
          </HTMLTable>
        </li>
      </UL>
    </WebActionCollapse>
  )
}
