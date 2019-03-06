import {
  Button,
  Card,
  Classes,
  Collapse,
  ControlGroup,
  H3,
  H5,
  Icon,
  Intent,
  Menu,
  MenuItem,
  Pre,
  Spinner,
  Tag,
  Tooltip
} from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { FlexContainer } from "@misk/core"
import { onChangeToggleFnCall, simpleSelect } from "@misk/simpleredux"
import { HTTPMethod } from "http-method-enum"
import * as React from "react"
import { connect } from "react-redux"
import styled from "styled-components"
import {
  IDispatchProps,
  IState,
  IWebActionInternal,
  rootDispatcher,
  rootSelectors
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
  display: block;
  height: 36px;
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

const RequestResponeContentTypes = (props: { action: IWebActionInternal }) => (
  <span>
    <span>{props.action.requestMediaTypes}</span>{" "}
    <Icon icon={IconNames.ARROW_RIGHT} />{" "}
    <span>{props.action.responseMediaType}</span>
  </span>
)

const Metadata = (
  props: {
    content: string | JSX.Element
    label: string
    tooltip: string | JSX.Element
    action: IWebActionInternal
  } & IState &
    IDispatchProps
) => {
  return (
    <MenuItem
      label={props.label}
      onClick={onChangeToggleFnCall(
        props.simpleFormToggle,
        `WebAction::${props.action.pathPattern}::${props.label}`,
        props.simpleForm
      )}
      text={<Tooltip content={props.tooltip}>{props.content}</Tooltip>}
    />
  )
}

const FilterTag = "WebActions::FilterForm"

//TODO(adrw) upstream to @misk/core
const HTTPMethodIntent: { [method in HTTPMethod]: Intent } = {
  [HTTPMethod.CONNECT]: Intent.DANGER,
  [HTTPMethod.DELETE]: Intent.DANGER,
  [HTTPMethod.GET]: Intent.PRIMARY,
  [HTTPMethod.HEAD]: Intent.WARNING,
  [HTTPMethod.OPTIONS]: Intent.NONE,
  [HTTPMethod.PATCH]: Intent.SUCCESS,
  [HTTPMethod.POST]: Intent.SUCCESS,
  [HTTPMethod.PUT]: Intent.SUCCESS,
  [HTTPMethod.TRACE]: Intent.NONE
}

const MethodTag = (props: { method: HTTPMethod }) => (
  <FloatRight>
    <Tag large={true} intent={HTTPMethodIntent[props.method]}>
      {props.method}
    </Tag>
  </FloatRight>
)

const WebAction = (
  props: { action: IWebActionInternal } & IState & IDispatchProps
) => {
  return (
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
                {...props}
              />
              <Metadata
                content={props.action.allowedServices}
                label={"Services"}
                tooltip={props.action.allowedServices}
                {...props}
              />
              <Metadata
                content={props.action.allowedRoles}
                label={"Roles"}
                tooltip={props.action.allowedRoles}
                {...props}
              />
              <Metadata
                content={props.action.authFunctionAnnotations[0]}
                label={"Access"}
                tooltip={props.action.authFunctionAnnotations[0]}
                {...props}
              />
            </MetadataMenu>
          </Column>
          <Column>
            <MetadataMenu>
              <Metadata
                content={<RequestResponeContentTypes action={props.action} />}
                label={"Content Types"}
                tooltip={<RequestResponeContentTypes action={props.action} />}
                {...props}
              />
              <Metadata
                content={"Application Interceptors"}
                label={`(${props.action.applicationInterceptors.length})`}
                tooltip={"Application Interceptors"}
                {...props}
              />
              <Collapse
                isOpen={
                  props.action.applicationInterceptors.length &&
                  simpleSelect(
                    props.simpleForm,
                    `WebAction::${props.action.pathPattern}::(${
                      props.action.applicationInterceptors.length
                    })`,
                    "data"
                  )
                }
              >
                {props.action.applicationInterceptors.map(i => (
                  <MenuItem text={<Tooltip content={i}>{i}</Tooltip>} />
                ))}
              </Collapse>

              <Metadata
                content={"Network Interceptors"}
                label={`(${props.action.networkInterceptors.length})`}
                tooltip={"Network Interceptors"}
                {...props}
              />
              <Collapse
                isOpen={
                  props.action.networkInterceptors.length &&
                  simpleSelect(
                    props.simpleForm,
                    `WebAction::${props.action.pathPattern}::(${
                      props.action.networkInterceptors.length
                    })`,
                    "data"
                  )
                }
              >
                {props.action.networkInterceptors.map(i => (
                  <MenuItem text={<Tooltip content={i}>{i}</Tooltip>} />
                ))}
              </Collapse>
            </MetadataMenu>
          </Column>
        </FlexContainer>
      </Card>
      <br />
    </div>
  )
}

const WebActions = (
  props: { metadata: IWebActionInternal[] } & IState & IDispatchProps
) => {
  if (props.metadata) {
    return (
      <div>
        {props.metadata.map((action: any) => (
          <WebAction action={action} {...props} />
        ))}
      </div>
    )
  } else {
    return <Spinner />
  }
}

export const FilterWebActions = (props: IState & IDispatchProps) => {
  return (
    <div>
      <Pre>
        sampleFormData:
        {JSON.stringify(simpleSelect(props.simpleForm, FilterTag), null, 2)}
      </Pre>
      <ControlGroup fill={true} vertical={false}>
        <Button icon={IconNames.FILTER} />
        {/* TODO(adrw) Use multiselect to do autocomplete filters */}
      </ControlGroup>
    </div>
  )
}

/**
 * Empty text for use with BlueprintJS Skeleton class for mocking loading UIs
 * https://blueprintjs.com/docs/#core/components/skeleton
 */
const SkeletonText = () => (
  <span className={Classes.SKELETON}>{"Lorem ipsum"}</span>
)

const SkeletonWebActions = () => (
  <Card>
    <Header>
      {[HTTPMethod.DELETE, HTTPMethod.GET, HTTPMethod.PUT, HTTPMethod.POST].map(
        m => (
          <MethodTag method={m} />
        )
      )}
      <FloatLeft>
        <H3 className={Classes.SKELETON}>{"AnotherWebAction"}</H3>
      </FloatLeft>
      <FloatLeft>
        <CodeTag large={true}>{<SkeletonText />}</CodeTag>
      </FloatLeft>
    </Header>
    <FlexContainer>
      <Column>
        <MetadataMenu>
          <MenuItem label={"Function"} text={<SkeletonText />} />
          <MenuItem label={"Services"} text={<SkeletonText />} />
          <MenuItem label={"Roles"} text={<SkeletonText />} />
          <MenuItem label={"Access"} text={<SkeletonText />} />
        </MetadataMenu>
      </Column>
      <Column>
        <MetadataMenu>
          <MenuItem label={"Content Types"} text={<SkeletonText />} />
          <MenuItem
            label={"Application Interceptors"}
            text={<SkeletonText />}
          />
          <MenuItem label={"Network Interceptors"} text={<SkeletonText />} />
        </MetadataMenu>
      </Column>
    </FlexContainer>
  </Card>
)

const WebActionsContainer = (props: IState & IDispatchProps) => {
  const metadata = simpleSelect(props.webActions, "metadata")
  if (metadata.length > 0) {
    return (
      <div>
        <WebActions metadata={metadata} {...props} />
      </div>
    )
  } else {
    return <SkeletonWebActions />
  }
}

const mapStateToProps = (state: IState) => rootSelectors(state)

export default connect(
  mapStateToProps,
  rootDispatcher
)(WebActionsContainer)
