import { Collapse, Icon, Menu, MenuItem, Tooltip } from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { onChangeToggleFnCall, simpleSelect } from "@misk/simpleredux"
import * as React from "react"
import styled from "styled-components"
import { IDispatchProps, IState } from "../ducks"

export const MetadataMenu = styled(Menu)`
  li {
    margin-bottom: 0;
  }
`

/**
 * Single cell component for a piece of metadata
 * @param props
 *  * content: primary content that is displayed (ie. eng, service-owners)
 *  * label: right aligned faded text that is a label for the data (ie. Roles)
 *  * tooltip: text displayed in tooltip when content is hovered over.
 *      Separate definition from content in the case that a truncated length
 *      is displayed but the full length of content is displayed in tooltip
 */
export const Metadata = (props: {
  content: string | JSX.Element
  label?: string
  labelElement?: JSX.Element
  onClick?: (event: any) => void
  tooltip?: string | JSX.Element
}) => {
  if (props.tooltip) {
    return (
      <MenuItem
        {...props}
        text={<Tooltip content={props.tooltip}>{props.content}</Tooltip>}
      />
    )
  } else {
    return <MenuItem {...props} text={<Tooltip>{props.content}</Tooltip>} />
  }
}

/**
 * Metadata that slides out content below when clicked
 * @param props : includes same props as Metadata with a few additional
 *  * children: any components to display when the Metadata is clicked
 *  * tag: string to use in @misk/SimpleRedux/SimpleForm to register Metadata clicks
 *  * IState: include connected State from parent container
 *      Provides access to @misk/SimpleRedux/SimpleForm substate in Redux
 *  * IDispatchProps: include connected dispatch object from parent container
 *      Provides access to @misk/SimpleRedux/SimpleForm input handlers
 */
export const MetadataCollapse = (
  props: {
    children: any
    content: string | JSX.Element
    isOpen?: boolean
    label?: string
    labelElement?: JSX.Element
    tag: string
  } & IState &
    IDispatchProps
) => {
  const { children } = props
  delete props.children
  return (
    <div>
      <Metadata
        {...props}
        content={
          <span>
            {simpleSelect(props.simpleForm, props.tag, "data") ? (
              <Icon icon={IconNames.CARET_DOWN} />
            ) : (
              <Icon icon={IconNames.CARET_RIGHT} />
            )}{" "}
            {props.content}
          </span>
        }
        onClick={onChangeToggleFnCall(
          props.simpleFormToggle,
          props.tag,
          props.simpleForm
        )}
      />
      <Collapse isOpen={simpleSelect(props.simpleForm, props.tag, "data")}>
        {children}
      </Collapse>
    </div>
  )
}
