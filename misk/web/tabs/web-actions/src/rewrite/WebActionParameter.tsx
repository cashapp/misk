import React from "react"
import { ParameterMetaData } from "./types"

interface Props {
  parameter: ParameterMetaData
}
export default function WebActionParameter({ parameter }: Props) {
  return (
    <tr>
      <td>{parameter.annotations.join(",")}</td>
      <td>{parameter.name}</td>
      <td>{parameter.type}</td>
    </tr>
  )
}
