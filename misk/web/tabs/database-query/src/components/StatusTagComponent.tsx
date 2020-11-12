import { Tag } from "@blueprintjs/core"
import { HTTPStatusCodeIntent } from "@misk/core"
import * as React from "react"

export const StatusTagComponent = (props: {
  status: number
  statusText: string
}) => {
  const { status, statusText } = props
  if (status && statusText) {
    const intent = HTTPStatusCodeIntent(status)
    return <Tag intent={intent}>{`${status} ${statusText}`}</Tag>
  } else {
    return <span />
  }
}
