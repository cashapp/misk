/** @jsx jsx */
import { Button, Tooltip } from "@blueprintjs/core"
import { IconName } from "@blueprintjs/icons"
import { jsx } from "@emotion/react"
import { handler } from "src/form-builder"
import { cssButton, cssTooltip } from "./CommonComponents"

export const QuantityButton = (props: {
  changeFn: () => void
  content: string
  icon: IconName
}) => (
  <Tooltip css={cssTooltip} content={props.content} lazy={true}>
    <Button
      css={cssButton}
      icon={props.icon}
      onClick={handler.handle(props.changeFn)}
    />
  </Tooltip>
)
