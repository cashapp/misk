import {
  Card,
  Classes,
  Collapse,
  H3,
  H5,
  Icon,
  Menu,
  MenuItem,
  Spinner,
  Tag,
  Tooltip
} from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { FlexContainer } from "@misk/core"
import { onChangeToggleFnCall, simpleSelect } from "@misk/simpleredux"
import { HTTPMethod } from "http-method-enum"
import { chain } from "lodash"
import * as React from "react"
import styled from "styled-components"
import {
  FilterWebActionsComponent,
  SendRequestCollapseComponent
} from "../components"
import {
  HTTPMethodIntent,
  IDispatchProps,
  IState,
  IWebActionInternal,
  WebActionInternalLabel
} from "../ducks"

const FloatLeft = styled.span`
  float: left;
  margin: 0 10px 0 0;
`

const FloatRight = styled.span`
  float: right;
  margin: 0 0 0 10px;
`

const CodeTag = styled(Tag)`
  font-family: monospace;
`

const Header = styled.div`
  display: inline-block;
`

const Column = styled.div`
  flex-grow: 1;
  flex-basis: 0;
  min-width: 320px;
`

const MetadataMenu = styled(Menu)`
  li {
    margin-bottom: 0;
  }
`

/**
 * Used in rendering the Content Types metadata request -> response
 */
const RequestResponeContentTypes = (props: { action: IWebActionInternal }) => (
  <span>
    <span>{props.action.requestMediaTypes}</span>{" "}
    <Icon icon={IconNames.ARROW_RIGHT} />{" "}
    <span>{props.action.responseMediaType}</span>
  </span>
)

/**
 * Single cell component for a piece of metadata
 * @param props
 *  * content: primary content that is displayed (ie. eng, service-owners)
 *  * label: right aligned faded text that is a label for the data (ie. Roles)
 *  * tooltip: text displayed in tooltip when content is hovered over.
 *      Separate definition from content in the case that a truncated length
 *      is displayed but the full length of content is displayed in tooltip
 */
const Metadata = (
  props: {
    content: string | JSX.Element
    label: string
    tooltip?: string | JSX.Element
  } & any
) => {
  if (props.tooltip) {
    return (
      <MenuItem
        label={props.label}
        text={<Tooltip content={props.tooltip}>{props.content}</Tooltip>}
        {...props}
      />
    )
  } else {
    return (
      <MenuItem
        label={props.label}
        text={<Tooltip>{props.content}</Tooltip>}
        {...props}
      />
    )
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
const MetadataCollapse = (
  props: {
    children: any
    content: string | JSX.Element
    label: string
    tag: string
    tooltip?: string | JSX.Element
  } & IState &
    IDispatchProps
) => (
  <div>
    <Metadata
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
      label={props.label}
      onClick={onChangeToggleFnCall(
        props.simpleFormToggle,
        props.tag,
        props.simpleForm
      )}
    />
    <Collapse isOpen={simpleSelect(props.simpleForm, props.tag, "data")}>
      {props.children}
    </Collapse>
  </div>
)

/**
 * Renders HTTP Method tags for each Web Action card
 */
const MethodTag = (props: { method: HTTPMethod }) => (
  <FloatRight>
    <Tag large={true} intent={HTTPMethodIntent[props.method]}>
      {props.method}
    </Tag>
  </FloatRight>
)

/**
 * Web Action Card rendered for each bound Web Action
 */
const WebAction = (
  props: { action: IWebActionInternal; tag: string } & IState & IDispatchProps
) => (
  <div>
    <Card>
      <Header>
        {props.action.dispatchMechanism.map(m => (
          <MethodTag method={m} />
        ))}
        <FloatLeft>
          <H3>{props.action.name}</H3>
        </FloatLeft>
        <FloatLeft>
          <CodeTag large={true}>{props.action.pathPattern}</CodeTag>
        </FloatLeft>
      </Header>
      {props.action.nonAccessOrTypeFunctionAnnotations.map(a => (
        <H5>{a}</H5>
      ))}
      <FlexContainer>
        <Column>
          <MetadataMenu>
            <Metadata
              content={props.action.function}
              label={"Function"}
              tooltip={props.action.function}
            />
            <Metadata
              content={props.action.allowedServices}
              label={"Services"}
              tooltip={props.action.allowedServices}
            />
            <Metadata
              content={props.action.allowedRoles}
              label={"Roles"}
              tooltip={props.action.allowedRoles}
            />
            <Metadata
              content={props.action.authFunctionAnnotations[0]}
              label={"Access"}
              tooltip={props.action.authFunctionAnnotations[0]}
            />
          </MetadataMenu>
        </Column>
        <Column>
          <MetadataMenu>
            <Metadata
              content={<RequestResponeContentTypes action={props.action} />}
              label={"Content Types"}
              tooltip={<RequestResponeContentTypes action={props.action} />}
            />
            <MetadataCollapse
              {...props}
              content={"Application Interceptors"}
              label={`(${props.action.applicationInterceptors.length})`}
              tag={`${props.tag}::ApplicationInterceptors`}
            >
              {props.action.applicationInterceptors.map(i => (
                <MenuItem text={<Tooltip content={i}>{i}</Tooltip>} />
              ))}
            </MetadataCollapse>
            <MetadataCollapse
              {...props}
              content={"Network Interceptors"}
              label={`(${props.action.networkInterceptors.length})`}
              tag={`${props.tag}::NetworkInterceptors`}
            >
              {props.action.networkInterceptors.map(i => (
                <MenuItem text={<Tooltip content={i}>{i}</Tooltip>} />
              ))}
            </MetadataCollapse>
            <MetadataCollapse
              {...props}
              content={"Send a Request"}
              label={""}
              tag={`${props.tag}::Request`}
            >
              <span />
            </MetadataCollapse>
          </MetadataMenu>
        </Column>
      </FlexContainer>
      <SendRequestCollapseComponent {...props} />
    </Card>
    <br />
  </div>
)

/**
 * Loops over bound Web Actions array (metadata) and renders Web Action card
 */
const WebActions = (
  props: { metadata: IWebActionInternal[]; tag: string } & IState &
    IDispatchProps
) => {
  if (props.metadata) {
    return (
      <div>
        {props.metadata.map((action: any) => (
          <WebAction
            {...props}
            action={action}
            tag={`${props.tag}::${action.name}${action.pathPattern}`}
          />
        ))}
      </div>
    )
  } else {
    return <Spinner />
  }
}

/**
 * Empty Web Action Card UI for use with BlueprintJS Skeleton class in loading UIs
 * https://blueprintjs.com/docs/#core/components/skeleton
 */
const SkeletonText = () => (
  <span className={Classes.SKELETON}>{"Lorem ipsum"}</span>
)

const SkeletonWebActions = () => (
  <Card>
    <Header>
      {[HTTPMethod.GET].map(m => (
        <MethodTag method={m} />
      ))}
      <FloatLeft>
        <H3 className={Classes.SKELETON}>{"AnotherSimpleWebAction"}</H3>
      </FloatLeft>
      <FloatLeft>
        <CodeTag large={true}>{<SkeletonText />}</CodeTag>
      </FloatLeft>
    </Header>
    <FlexContainer>
      <Column>
        <MetadataMenu>
          <Metadata label={"Function"} content={<SkeletonText />} />
          <Metadata label={"Services"} content={<SkeletonText />} />
          <Metadata label={"Roles"} content={<SkeletonText />} />
          <Metadata label={"Access"} content={<SkeletonText />} />
        </MetadataMenu>
      </Column>
      <Column>
        <MetadataMenu>
          <Metadata label={"Content Types"} content={<SkeletonText />} />
          <Metadata
            label={"Application Interceptors"}
            text={<SkeletonText />}
          />
          <Metadata label={"Network Interceptors"} content={<SkeletonText />} />
          <Metadata label={"Send a Request"} content={<SkeletonText />} />
        </MetadataMenu>
      </Column>
    </FlexContainer>
  </Card>
)

export const WebActionsComponent = (
  props: IState & IDispatchProps & { tag: string }
) => {
  const metadata = simpleSelect(props.webActions, "metadata")
  const filterTag = `${props.tag}::Filter`
  if (metadata.length > 0) {
    const filterKey =
      WebActionInternalLabel[
        simpleSelect(props.simpleForm, `${filterTag}::HTMLSelect`, "data") ||
          "All Metadata"
      ]
    const filterValue = simpleSelect(
      props.simpleForm,
      `${filterTag}::Input`,
      "data"
    )
    const filteredMetadata = chain(metadata)
      .filter((action: IWebActionInternal) =>
        ((action as any)[filterKey] || "")
          .toString()
          .toLowerCase()
          .includes(filterValue.toLowerCase())
      )
      .value()
    return (
      <div>
        <FilterWebActionsComponent {...props} />
        <WebActions
          metadata={filteredMetadata as IWebActionInternal[]}
          {...props}
        />
      </div>
    )
  } else {
    // Displays mock of 5 Web Action cards which fill in when data is available
    return (
      <div>
        <FilterWebActionsComponent {...props} />
        <SkeletonWebActions />
        <br />
        <SkeletonWebActions />
        <br />
        <SkeletonWebActions />
        <br />
        <SkeletonWebActions />
        <br />
        <SkeletonWebActions />
      </div>
    )
  }
}
