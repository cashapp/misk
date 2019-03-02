import {
  Card,
  H3,
  H5,
  Icon,
  Intent,
  Menu,
  MenuItem,
  Tag,
  Tooltip,
  Spinner
} from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { FlexContainer } from "@misk/core"
import { simpleSelect, simpleType } from "@misk/simpleredux"
import * as React from "react"
import { connect } from "react-redux"
import styled from "styled-components"
import { IDispatchProps, IState, rootDispatcher, rootSelectors } from "../ducks"

export interface IWebAction {
  applicationInterceptors: string[]
  dispatchMechanism: string
  function: string
  functionAnnotations: string[]
  name: string
  networkInterceptors: string[]
  parameterTypes: string[]
  pathPattern: string
  requestMediaTypes: string[]
  responseMediaType: string
  returnType: string
}

const HttpMethodTag = styled.span`
  float: right;
`

const Column = styled.div`
  flex-grow: 1;
  flex-basis: 0;
  min-width: 320px;
`

const MenuItemProps = {
  multiline: true
}

const ComingSoon = () => (
  <span>
    <span>{"Coming soon "}</span>
    <Icon icon={IconNames.AIRPLANE} />
  </span>
)

const RequestResponeContentTypes = (props: { action: IWebAction }) => (
  <span>
    <span>{props.action.requestMediaTypes}</span>{" "}
    <Icon icon={IconNames.ARROW_RIGHT} />{" "}
    <span>{props.action.responseMediaType}</span>
  </span>
)

enum HttpMethod {
  "DELTE" = "DELETE",
  "GET" = "GET",
  "HEAD" = "HEAD",
  "POST" = "POST",
  "PATCH" = "PATCH",
  "PUT" = "PUT"
}

const HttpMethodIntent = {
  DELETE: Intent.DANGER,
  GET: Intent.PRIMARY,
  HEAD: Intent.WARNING,
  PATCH: Intent.SUCCESS,
  POST: Intent.SUCCESS,
  PUT: Intent.SUCCESS
}

const filterAnnotations = (functionAnnotations: string[]) =>
  functionAnnotations.filter(
    a =>
      !(
        a.includes("RequestContentType") ||
        a.includes("ResponseContentType") ||
        a.includes("Access") ||
        a.includes("authz") ||
        a.toUpperCase().includes(HttpMethod.DELTE) ||
        a.toUpperCase().includes(HttpMethod.GET) ||
        a.toUpperCase().includes(HttpMethod.HEAD) ||
        a.toUpperCase().includes(HttpMethod.PATCH) ||
        a.toUpperCase().includes(HttpMethod.POST) ||
        a.toUpperCase().includes(HttpMethod.PUT)
      )
  )

const securityAnnotation = (functionAnnotations: string[]) =>
  functionAnnotations.filter(
    a => a.includes("Access") || a.includes("authz")
  )[0]

export const WebAction = (props: { action: IWebAction }) => {
  return (
    <div>
      <Card>
        <HttpMethodTag>
          <Tag
            large={true}
            intent={
              HttpMethodIntent[props.action.dispatchMechanism as HttpMethod]
            }
          >
            {props.action.dispatchMechanism}
          </Tag>
        </HttpMethodTag>
        <H3>{props.action.pathPattern}</H3>
        {filterAnnotations(props.action.functionAnnotations).map(a => (
          <H5>{a}</H5>
        ))}
        <FlexContainer>
          <Column>
            <Menu>
              <MenuItem
                label={"Action"}
                text={
                  <Tooltip content={props.action.function}>
                    {props.action.name}
                  </Tooltip>
                }
                {...MenuItemProps}
              />
              <MenuItem
                label={"Services"}
                text={<ComingSoon />}
                {...MenuItemProps}
              />
              <MenuItem
                label={"Roles"}
                text={<ComingSoon />}
                {...MenuItemProps}
              />
              <MenuItem
                label={"Access"}
                text={
                  <Tooltip
                    content={securityAnnotation(
                      props.action.functionAnnotations
                    )}
                  >
                    {securityAnnotation(props.action.functionAnnotations)
                      .split(".")
                      .slice(-1)}
                  </Tooltip>
                }
                {...MenuItemProps}
              />
            </Menu>
          </Column>
          <Column>
            <Menu>
              <MenuItem
                active={false}
                label={"Content Types"}
                text={
                  <Tooltip
                    content={
                      <RequestResponeContentTypes action={props.action} />
                    }
                  >
                    <RequestResponeContentTypes action={props.action} />
                  </Tooltip>
                }
                {...MenuItemProps}
              />
              <MenuItem
                label={`Application Interceptors (${
                  props.action.applicationInterceptors.length
                })`}
                text={props.action.applicationInterceptors.pop()}
                {...MenuItemProps}
              >
                {props.action.applicationInterceptors.map(i => (
                  <MenuItem text={i} {...MenuItemProps} />
                ))}
              </MenuItem>
              <MenuItem
                label={`Network Interceptors (${
                  props.action.networkInterceptors.length
                })`}
                text={props.action.networkInterceptors[0]}
                {...MenuItemProps}
              >
                {props.action.networkInterceptors.map(i => (
                  <MenuItem text={i} {...MenuItemProps} />
                ))}
              </MenuItem>
            </Menu>
          </Column>
        </FlexContainer>
      </Card>
      <br />
    </div>
  )
}

export const WebActions = (props: { metadata: IWebAction[] }) => {
  console.log(props)
  if (props.metadata) {
    return (
      <div>
        {props.metadata.map((action: any) => (
          <WebAction action={action} />
        ))}
      </div>
    )
  } else {
    return <Spinner />
  }
}

export const WebActionsContainer = (props: IState & IDispatchProps) => {
  const tag = "WebAction"
  const metadata = simpleSelect(
    props.simpleNetwork,
    tag,
    "webActionMetadata",
    simpleType.array
  )
  return (
    <div>
      <WebActions metadata={metadata} />
    </div>
  )
}

const mapStateToProps = (state: IState) => rootSelectors(state)

export default connect(
  mapStateToProps,
  rootDispatcher
)(WebActionsContainer)
