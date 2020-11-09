/** @jsx jsx */
import { Card, H3, Intent, Menu, Tag } from "@blueprintjs/core"
import { jsx } from "@emotion/core"
import { FlexContainer } from "@misk/core"
// import { connect } from "react-redux"
import {
  cssCodeTag,
  cssColumn,
  cssFloatLeft,
  cssFloatRight,
  cssHeader,
  cssMetadataMenu,
  MetadataCollapse
} from "../components"
import {
  IDatabaseQueryMetadataAPI
} from "."

/**
 * Web Action Card rendered for each bound Web Action
 */
export const DatabaseQueryCardContainer = (props: {
  databaseQuery: IDatabaseQueryMetadataAPI
  tag: string
}) => (
  <div>
    <Card interactive={true}>
      <div css={cssHeader}>
        <Tag
          css={cssFloatRight}
          key={props.databaseQuery.entityClass}
          intent={Intent.PRIMARY}
          large={true}
        >
          {props.databaseQuery.entityClass}
        </Tag>
        <span css={cssFloatLeft}>
          <H3>{props.databaseQuery.queryClass}</H3>
        </span>
        <span css={cssFloatLeft}>
          <Tag css={cssCodeTag} large={true}>
            {props.databaseQuery.table}
          </Tag>
        </span>
      </div>
      {/* {props.databaseQuery.nonAccessOrTypeFunctionAnnotations.map((a, index) => (
        <H5 key={index}>{a}</H5>
      ))} */}
      <FlexContainer>
        <div css={cssColumn}>
          <Menu css={cssMetadataMenu}>
            <MetadataCollapse
              content={props.databaseQuery.allowedServices}
              countLabel={true}
              label={"Services"}
              tag={`${props.tag}::Services`}
            />
            <MetadataCollapse
              content={props.databaseQuery.allowedCapabilities}
              countLabel={true}
              label={"Roles"}
              tag={`${props.tag}::Roles`}
            />
            <MetadataCollapse
              content={props.databaseQuery.accessAnnotation}
              label={"Access"}
              tag={`${props.tag}::Access`}
            />
          </Menu>
        </div>
        <div css={cssColumn}>
          <Menu css={cssMetadataMenu}>
            <MetadataCollapse
              children={<span />}
              tag={`${props.tag}::ButtonSendRequest`}
              text={"Run a Query"}
            />
          </Menu>
        </div>
      </FlexContainer>
      {/* <SendQueryCollapseContainer databaseQuery={props.databaseQuery} tag={props.tag} /> */}
    </Card>
    <br />
  </div>
)
