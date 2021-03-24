import React from "react"
import { Spinner } from "@blueprintjs/core"

export default function LoadingState() {
  return (
    <div
      style={{
        width: "100%",
        height: "100vh",
        display: "flex",
        justifyContent: "center"
      }}
    >
      <Spinner intent="success" size={100} />
    </div>
  )
}
