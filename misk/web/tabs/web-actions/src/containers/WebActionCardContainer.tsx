/** @jsx jsx */
import { Card, H3, H5, Menu, Tag } from "@blueprintjs/core"
import { jsx } from "@emotion/core"
import { FlexContainer } from "@misk/core"
import { connect } from "react-redux"
import {
  cssCodeTag,
  cssColumn,
  cssFloatLeft,
  cssHeader,
  cssMetadataMenu,
  MetadataCollapse,
  MethodTag,
  RequestResponseContentTypesSpan,
  requestResponseContentTypesString
} from "../components"
import { SendRequestCollapseContainer } from "../containers"
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
    <Card interactive={true}>
      <div css={cssHeader}>
        {props.action.httpMethod.map(m => (
          <MethodTag key={m} method={m} />
        ))}
        <span css={cssFloatLeft}>
          <H3>{props.action.name}</H3>
        </span>
        <span css={cssFloatLeft}>
          <Tag css={cssCodeTag} large={true}>
            {props.action.pathPattern}
          </Tag>
        </span>
      </div>
      {props.action.nonAccessOrTypeFunctionAnnotations.map((a, index) => (
        <H5 key={index}>{a}</H5>
      ))}
      <FlexContainer>
        <div css={cssColumn}>
          <Menu css={cssMetadataMenu}>
            <MetadataCollapse
              content={props.action.function}
              label={"Function"}
              tag={`${props.tag}::Function`}
              text={
                props.action.function
                  .split("(")[0]
                  .split(".")
                  .slice(-1)[0]
              }
            />
            <MetadataCollapse
              content={props.action.allowedServices}
              countLabel={true}
              label={"Services"}
              tag={`${props.tag}::Services`}
            />
            <MetadataCollapse
              content={props.action.allowedCapabilities}
              countLabel={true}
              label={"Roles"}
              tag={`${props.tag}::Roles`}
            />
            <MetadataCollapse
              content={props.action.authFunctionAnnotations}
              label={"Access"}
              tag={`${props.tag}::Access`}
            />
          </Menu>
        </div>
        <div css={cssColumn}>
          <Menu css={cssMetadataMenu}>
            <MetadataCollapse
              content={requestResponseContentTypesString(props.action)}
              data={requestResponseContentTypesString(props.action)}
              label={"Content Types"}
              tag={`${props.tag}::ContentTypes`}
              text={<RequestResponseContentTypesSpan action={props.action} />}
            />
            <MetadataCollapse
              content={props.action.applicationInterceptors}
              countLabel={true}
              label={"Application Interceptors"}
              tag={`${props.tag}::ApplicationInterceptors`}
              text={props.action.applicationInterceptors.join(", ")}
            />
            <MetadataCollapse
              content={props.action.networkInterceptors}
              countLabel={true}
              label={"Network Interceptors"}
              tag={`${props.tag}::NetworkInterceptors`}
            />
            <MetadataCollapse
              children={<span />}
              tag={`${props.tag}::ButtonSendRequest`}
              text={"Send a Request"}
            />
          </Menu>
        </div>
      </FlexContainer>
      <SendRequestCollapseContainer action={props.action} tag={props.tag} />
    </Card>
    <br />
  </div>
)

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(WebActionCardContainer)
