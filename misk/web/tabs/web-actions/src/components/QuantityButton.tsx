/** @jsx jsx */
import { Button, Tooltip } from "@blueprintjs/core"
import { IconName } from "@blueprintjs/icons"
import { jsx } from "@emotion/core"
import { onChangeFnCall } from "@misk/simpleredux"
import { IWebActionInternal } from "../ducks"
import { cssButton, cssTooltip } from "./CommonComponents"

export const QuantityButton = (props: {
  id: string
  action: IWebActionInternal
  changeFn: any
  content: string
  icon: IconName
  oldState: any
}) => (
  <Tooltip css={cssTooltip} content={props.content} lazy={true}>
    <Button
      css={cssButton}
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
