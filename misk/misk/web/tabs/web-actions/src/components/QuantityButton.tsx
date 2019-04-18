import { Button, Tooltip } from "@blueprintjs/core"
import { IconName } from "@blueprintjs/icons"
import { onChangeFnCall } from "@misk/simpleredux"
import * as React from "react"
import { IWebActionInternal } from "../ducks"

export const QuantityButton = (props: {
  id: string
  action: IWebActionInternal
  changeFn: any
  content: string
  icon: IconName
  oldState: any
}) => (
  <Tooltip content={props.content}>
    <Button
      icon={props.icon}
      onClick={onChangeFnCall(
        props.changeFn,
        props.id,
        props.action,
        props.oldState
      )}
    />
  </Tooltip>
)
