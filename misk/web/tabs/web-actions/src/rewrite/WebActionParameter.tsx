import React from "react"
import { ParameterMetaData } from "./types"

interface Props {
  parameter: ParameterMetaData
}
export default function WebActionParameter({ parameter }: Props) {
  const cellStyle = { border: "none", padding: "0px 11px 0px 0px" }
  return (
    <tr>
      <td style={cellStyle}>{parameter.annotations.join(",")}</td>
      <td style={cellStyle}>{parameter.name}</td>
      <td style={cellStyle}>{parameter.type}</td>
    </tr>
  )
}
