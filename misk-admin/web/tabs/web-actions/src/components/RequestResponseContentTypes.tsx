import { Icon } from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import * as React from "react"
import { IWebActionInternal } from "../ducks"

/**
 * Used in rendering the Content Types metadata request -> response
 */
export const RequestResponseContentTypesSpan = (props: {
  action: IWebActionInternal
}) => (
  <span>
    <span>{props.action.requestMediaTypes}</span>{" "}
    <Icon icon={IconNames.ARROW_RIGHT} />{" "}
    <span>{props.action.responseMediaType}</span>
  </span>
)

export const requestResponseContentTypesString = (action: IWebActionInternal) =>
  `${action.requestMediaTypes} -> ${action.responseMediaType}`
