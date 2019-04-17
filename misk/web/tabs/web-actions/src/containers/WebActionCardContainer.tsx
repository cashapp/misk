import { Card, H3, H5, MenuItem } from "@blueprintjs/core"
import { FlexContainer, WrapTextContainer } from "@misk/core"
import * as React from "react"
import { connect } from "react-redux"
import {
  CodeTag,
  Column,
  FloatLeft,
  Header,
  Metadata,
  MetadataCollapse,
  MetadataMenu,
  MethodTag,
  RequestResponeContentTypes,
  SendRequestCollapseComponent
} from "../components"
import {
  IDispatchProps,
  IState,
  IWebActionInternal,
  mapDispatchToProps,
  mapStateToProps
} from "../ducks"

/**
 * Web Action Card rendered for each bound Web Action
 */
const WebActionCardContainer = (
  props: { action: IWebActionInternal; tag: string } & IState & IDispatchProps
) => (
  <div>
    <Card>
      <Header>
        {props.action.dispatchMechanism.map(m => (
          <MethodTag key={m} method={m} />
        ))}
        <FloatLeft>
          <H3>{props.action.name}</H3>
        </FloatLeft>
        <FloatLeft>
          <CodeTag large={true}>{props.action.pathPattern}</CodeTag>
        </FloatLeft>
      </Header>
      {props.action.nonAccessOrTypeFunctionAnnotations.map((a, index) => (
        <H5 key={index}>{a}</H5>
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
              {props.action.applicationInterceptors.map((ai, index) => (
                <MenuItem
                  key={index}
                  text={<WrapTextContainer>{ai}</WrapTextContainer>}
                />
              ))}
            </MetadataCollapse>
            <MetadataCollapse
              {...props}
              content={"Network Interceptors"}
              label={`(${props.action.networkInterceptors.length})`}
              tag={`${props.tag}::NetworkInterceptors`}
            >
              {props.action.networkInterceptors.map((ni, index) => (
                <MenuItem
                  key={index}
                  text={<WrapTextContainer>{ni}</WrapTextContainer>}
                />
              ))}
            </MetadataCollapse>
            <MetadataCollapse
              {...props}
              content={"Send a Request"}
              label={""}
              tag={`${props.tag}::ButtonSendRequest`}
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

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(WebActionCardContainer)
