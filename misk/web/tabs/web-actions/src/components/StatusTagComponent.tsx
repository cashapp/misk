import { Tag } from "@blueprintjs/core"
import { HTTPStatusCodeIntent } from "@misk/core"
import * as React from "react"

export const StatusTagComponent = (props: { status: string[] }) => {
  const { status } = props
  if (status) {
    const intent = HTTPStatusCodeIntent(parseInt(status[0], 10))
    return <Tag intent={intent}>{status.join(" ")}</Tag>
  } else {
    return <span />
  }
}
