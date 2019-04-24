import {
  Button as BlueprintButton,
  Collapse,
  Colors,
  Icon,
  Menu,
  MenuItem,
  Tag,
  TextArea,
  Tooltip as BlueprintTooltip
} from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { HTTPMethodIntent, WrapTextContainer } from "@misk/core"
import {
  ISimpleFormState,
  onChangeToggleFnCall,
  onClickFnCall,
  simpleSelect
} from "@misk/simpleredux"
import copy from "copy-to-clipboard"
import HTTPMethod from "http-method-enum"
import * as React from "react"
import { connect } from "react-redux"
import styled from "styled-components"
import { IDispatchProps, mapDispatchToProps, mapStateToProps } from "../ducks"

export const FloatLeft = styled.span`
  float: left;
  margin: 5px 10px 0 0;
`

export const FloatRight = styled.span`
  float: right;
  margin: 5px 0 0 10px;
`

export const CodeTag = styled(Tag)`
  font-family: monospace;
`

export const Header = styled.div`
  display: inline-block;
`

export const Column = styled.div`
  flex-grow: 1;
  flex-basis: 0;
  min-width: 320px;
`

export const Button = styled(BlueprintButton)`
  line-height: normal;
  text-transform: inherit;
`

export const Tooltip = styled(BlueprintTooltip)`
  span.bp3-popover-target {
    display: inherit !important;
  }
`

/**
 * Renders HTTP Method tags for each Web Action card
 */
export const MethodTag = (props: { method: HTTPMethod }) => (
  <FloatRight>
    <Tag large={true} intent={HTTPMethodIntent[props.method]}>
      {props.method}
    </Tag>
  </FloatRight>
)

export const MetadataMenu = styled(Menu)`
  li {
    margin-bottom: 0;
  }
`

export const WrapTextArea = styled(TextArea)`
  white-space: pre-line;
`

const definedOrDefault = (item: any, booleanIfUndefined: boolean) =>
  item !== undefined ? item : booleanIfUndefined

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
        text={
          <Tooltip content={props.tooltip} lazy={true}>
            {props.content}
          </Tooltip>
        }
      />
    )
  } else {
    return (
      <MenuItem
        {...props}
        text={<Tooltip lazy={true}>{props.content}</Tooltip>}
      />
    )
  }
}

/**
 * Metadata pre-filled with content to support a click-to-copy-to-clipboard flow
 */
export const MetadataCopyToClipboard = (props: {
  clipboardLabelElement?: boolean
  content?: string | JSX.Element
  data: any
  description?: string
  label?: string
  labelElement?: JSX.Element
  onClick?: (event: any) => void
  tooltip?: string | JSX.Element
}) => {
  const description = props.description && `${props.description} `
  const content: string | JSX.Element =
    props.content || `Click to Copy ${description}to Clipboard`
  const labelElement: JSX.Element =
    props.labelElement ||
    (definedOrDefault(props.clipboardLabelElement, true) && (
      <Tooltip content={"Click to Copy to Clipboard"}>
        <Icon icon={IconNames.CLIPBOARD} />
      </Tooltip>
    ))
  const safeData =
    typeof props.data === "string" ? props.data : JSON.stringify(props.data)
  return (
    <Metadata
      content={content}
      label={props.label}
      labelElement={labelElement}
      onClick={onClickFnCall(copy, safeData)}
      tooltip={props.tooltip}
    />
  )
}

/**
 * MetadataCollapse is a generic container for all Metadata
 * It handles
 *  * Sliding out to reveal child content when clicked
 *  * Automatically showing overflow content below when clicked
 *  * Displaying a list of given content below when clicked
 *  * In most cases all content is "click-to-copy" enabled
 *
 * @param props : includes same props as Metadata with a few additional
 *  * children: any components to display when the Metadata is clicked
 *  * tag: string to use in @misk/SimpleRedux/SimpleForm to register Metadata clicks
 *  * IDispatchProps: include connected dispatch object from parent container
 *      Provides access to @misk/SimpleRedux/SimpleForm input handlers
 */

const UnconnectedMetadataCollapse = (
  props: {
    clipboardLabelElement?: boolean
    children?: any
    content?: string | string[] | JSX.Element | JSX.Element[]
    countLabel?: boolean
    data?: string
    isOpen?: boolean
    label?: string
    labelElement?: JSX.Element
    simpleForm: ISimpleFormState
    tag: string
    text?: string | JSX.Element
    tooltip?: string | JSX.Element
  } & IDispatchProps
) => {
  const content = Array.isArray(props.content) ? props.content : [props.content]
  const collapseIcon = simpleSelect(props.simpleForm, props.tag, "data") ? (
    <Icon icon={IconNames.CARET_DOWN} />
  ) : (
    <Icon icon={IconNames.CARET_RIGHT} />
  )
  return (
    <div>
      <Metadata
        content={
          <span>
            {props.children || content.length > 0 ? (
              collapseIcon
            ) : (
              <Icon color={Colors.LIGHT_GRAY1} icon={IconNames.CARET_RIGHT} />
            )}
            {props.text || props.children || content.join(", ")}
          </span>
        }
        data-testid={"metadata-collapse"}
        label={
          props.children || !props.countLabel
            ? props.label
            : `${props.label} (${content.length})`
        }
        labelElement={props.children ? props.labelElement : null}
        onClick={onChangeToggleFnCall(
          props.simpleFormToggle,
          props.tag,
          props.simpleForm
        )}
      />
      <Collapse isOpen={simpleSelect(props.simpleForm, props.tag, "data")}>
        {props.children
          ? props.children
          : content.map(c => (
              <MetadataCopyToClipboard
                clipboardLabelElement={definedOrDefault(
                  props.clipboardLabelElement,
                  true
                )}
                content={<WrapTextContainer>{c}</WrapTextContainer>}
                data={props.data || c}
                key={c.toString()}
                labelElement={props.labelElement}
              />
            ))}
      </Collapse>
    </div>
  )
}

export const MetadataCollapse = connect(
  mapStateToProps,
  mapDispatchToProps
)(UnconnectedMetadataCollapse)
