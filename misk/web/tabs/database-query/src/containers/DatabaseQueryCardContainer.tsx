/** @jsx jsx */
import { Card, H3, Intent, Menu, Tag } from "@blueprintjs/core"
import { jsx } from "@emotion/core"
import { FlexContainer } from "@misk/core"
import { useState } from "react"
import {
  cssCodeTag,
  cssColumn,
  cssFloatLeft,
  cssFloatRight,
  cssHeader,
  cssMetadataMenu,
  MetadataCollapse
} from "../components"
import { IDatabaseQueryMetadataAPI, RunQueryCollapseContainer } from "."

/**
 * Web Action Card rendered for each bound Web Action
 */
export const DatabaseQueryCardContainer = (props: {
  databaseQuery: IDatabaseQueryMetadataAPI
  tag: string
}) => {
  const [isOpenServices, setIsOpenServices] = useState(false)
  const [isOpenRoles, setIsOpenRoles] = useState(false)
  const [isOpenAccess, setIsOpenAccess] = useState(false)
  const [isOpenRunQuery, setIsOpenRunQuery] = useState(false)

  return (
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
        <FlexContainer>
          <div css={cssColumn}>
            <Menu css={cssMetadataMenu}>
              <MetadataCollapse
                content={props.databaseQuery.allowedServices}
                countLabel={true}
                label={"Services"}
                isOpen={isOpenServices}
                setIsOpen={setIsOpenServices}
              />
              <MetadataCollapse
                content={props.databaseQuery.allowedCapabilities}
                countLabel={true}
                label={"Roles"}
                isOpen={isOpenRoles}
                setIsOpen={setIsOpenRoles}
              />
              <MetadataCollapse
                content={props.databaseQuery.accessAnnotation}
                label={"Access"}
                isOpen={isOpenAccess}
                setIsOpen={setIsOpenAccess}
              />
            </Menu>
          </div>
          <div css={cssColumn}>
            <Menu css={cssMetadataMenu}>
              <MetadataCollapse
                children={<span />}
                isOpen={isOpenRunQuery}
                setIsOpen={setIsOpenRunQuery}
                text={"Run a Query"}
              />
            </Menu>
          </div>
        </FlexContainer>
        <RunQueryCollapseContainer
          databaseQuery={props.databaseQuery}
          isOpen={isOpenRunQuery}
          tag={props.tag}
        />
      </Card>
      <br />
    </div>
  )
}
