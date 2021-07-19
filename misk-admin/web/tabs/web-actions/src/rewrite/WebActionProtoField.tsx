import React from "react"
import { ProtoField } from "./types"

interface Props {
  field: ProtoField
}
export default function WebActionProtoField({ field }: Props) {
  const cellStyle = { border: "none", padding: "0px 11px 0px 0px" }
  return (
    <tr>
      <td style={cellStyle}>{field.name}</td>
      <td style={cellStyle}>{field.type}</td>
      <td style={cellStyle}>{field.repeated ? "repeated" : null}</td>
    </tr>
  )
}
