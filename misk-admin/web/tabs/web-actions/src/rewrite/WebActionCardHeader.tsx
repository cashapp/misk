import React from "react"
import { H3, Tag, Intent } from "@blueprintjs/core"
import Spacer from "./Spacer"

interface Props {
  title: string
  pathPattern: string
  httpMethod: string
}

export default function WebActionCardHeader({
  title,
  pathPattern,
  httpMethod
}: Props) {
  const httpMethodToIntent = (httpMethod: string) => {
    if (httpMethod == "POST") return Intent.SUCCESS
    if (httpMethod == "GET") return Intent.PRIMARY

    return Intent.NONE
  }

  return (
    <div style={{ display: "flex", alignItems: "center" }}>
      <H3 style={{ margin: 0 }}>{title}</H3>
      <Spacer size="medium" />
      <Tag large={true}>{pathPattern}</Tag>
      <Spacer size="medium" />
      <Tag large={true} intent={httpMethodToIntent(httpMethod)}>
        {httpMethod}
      </Tag>
    </div>
  )
}
