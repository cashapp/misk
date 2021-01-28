/** @jsx jsx */
import { Card, H3, Icon, Intent, Menu, Tag, Tooltip } from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { jsx } from "@emotion/react"
import { FlexContainer, Table } from "@misk/core"
import { useState } from "react"
import {
  cssCodeTag,
  cssColumn,
  cssFloatLeft,
  cssHeader,
  cssMetadataMenu,
  cssTableScroll,
  cssTooltip,
  MetadataCollapse,
  MetadataCopyToClipboard,
} from "../components"
import {
  IDatabaseQueryMetadataAPI,
  RunQueryCollapseContainer,
} from "../containers"
import {
  IConstraintMetadata,
  IOrderMetadata,
  ISelectMetadata,
  NoAdminDashboardDatabaseAccess,
} from "./DatabaseInterfaces"

/**
 * Web Action Card rendered for each bound Web Action
 */
export const DatabaseCardContainer = (props: {
  databaseQuery: IDatabaseQueryMetadataAPI
  tag: string
}) => {
  const [isOpenServices, setIsOpenServices] = useState(false)
  const [isOpenRoles, setIsOpenRoles] = useState(false)
  const [isOpenAccess, setIsOpenAccess] = useState(false)
  const [isOpenConstraints, setIsOpenConstraints] = useState(false)
  const [isOpenOrders, setIsOpenOrders] = useState(false)
  const [isOpenSelects, setIsOpenSelects] = useState(false)
  const [isOpenViewSchema, setIsOpenViewSchema] = useState(false)
  const [isOpenRunQuery, setIsOpenRunQuery] = useState(false)

  return (
    <div>
      <Card interactive={true}>
        <div css={cssHeader}>
          <span css={cssFloatLeft}>
            <H3>{props.databaseQuery.queryClass}</H3>
          </span>
          <Tag
            css={cssFloatLeft}
            key={props.databaseQuery.entityClass}
            intent={Intent.PRIMARY}
            large={true}
          >
            {props.databaseQuery.entityClass}
          </Tag>
          <span css={cssFloatLeft}>
            <Tag css={cssCodeTag} icon={IconNames.TH} large={true}>
              {props.databaseQuery.table}
            </Tag>
          </span>
        </div>
        <FlexContainer>
          <div css={cssColumn}>
            <Menu css={cssMetadataMenu}>
              <MetadataCollapse
                content={[<span />]}
                labelElement={<Icon icon={IconNames.DERIVE_COLUMN} />}
                isOpen={isOpenViewSchema}
                setIsOpen={setIsOpenViewSchema}
                text={"View Schema"}
              >
                <MetadataCopyToClipboard
                  data={JSON.stringify(
                    props.databaseQuery.entitySchema,
                    null,
                    2
                  )}
                  description={"Schema"}
                />
                <br />
                <div css={cssTableScroll}>
                  <Table
                    data={Object.entries(
                      props.databaseQuery.entitySchema
                    ).map(k => ({ column: k[0], type: k[1] }))}
                  />
                </div>
              </MetadataCollapse>
              {!props.databaseQuery.queryClass.endsWith("DynamicQuery") && (
                <span>
                  <MetadataCollapse
                    content={props.databaseQuery.constraints.map(
                      (constraint: IConstraintMetadata) => (
                        <span>
                          {constraint.name}{" "}
                          <Tag icon={IconNames.TH_FILTERED}>
                            {constraint.path}
                          </Tag>
                          {constraint.operator && (
                            <span>
                              {" "}
                              <Tag icon={IconNames.FUNCTION}>
                                {constraint.operator}
                              </Tag>
                            </span>
                          )}
                        </span>
                      )
                    )}
                    countLabel={true}
                    clipboardLabelElement={false}
                    label={""}
                    isOpen={isOpenConstraints}
                    setIsOpen={setIsOpenConstraints}
                    text={"Constraints"}
                  />
                  <MetadataCollapse
                    content={props.databaseQuery.orders.map(
                      (order: IOrderMetadata) => (
                        <span>
                          {order.name}{" "}
                          <Tag icon={IconNames.TH_FILTERED}>{order.path}</Tag>
                          {order.ascending && (
                            <span>
                              {" "}
                              <Tag
                                icon={
                                  (order.ascending && IconNames.ARROW_UP) ||
                                  IconNames.ARROW_DOWN
                                }
                              >
                                {(order.ascending && "Ascending") ||
                                  "Descending"}
                              </Tag>
                            </span>
                          )}
                        </span>
                      )
                    )}
                    countLabel={true}
                    clipboardLabelElement={false}
                    label={""}
                    isOpen={isOpenOrders}
                    setIsOpen={setIsOpenOrders}
                    text={"Orders"}
                  />
                  <MetadataCollapse
                    content={props.databaseQuery.selects.map(
                      (select: ISelectMetadata) => (
                        <span>
                          {select.name}{" "}
                          <Tag icon={IconNames.TH_FILTERED}>
                            {select.paths.map(path => `${path}, `)}
                          </Tag>
                        </span>
                      )
                    )}
                    countLabel={true}
                    clipboardLabelElement={false}
                    label={""}
                    isOpen={isOpenSelects}
                    setIsOpen={setIsOpenSelects}
                    text={"Selects"}
                  />
                </span>
              )}
            </Menu>
          </div>
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
              {props.databaseQuery.accessAnnotation ==
              NoAdminDashboardDatabaseAccess ? (
                <Tooltip
                  css={cssTooltip}
                  content={
                    "See Install Instructions for how to enable Read Queries for this DbEntity"
                  }
                  lazy={true}
                >
                  <MetadataCollapse
                    content={[]}
                    isOpen={isOpenRunQuery}
                    setIsOpen={() => false}
                    text={"Run a Query"}
                  />
                </Tooltip>
              ) : (
                <MetadataCollapse
                  content={[<span />]}
                  isOpen={isOpenRunQuery}
                  setIsOpen={setIsOpenRunQuery}
                  text={"Run a Query"}
                />
              )}
            </Menu>
          </div>
        </FlexContainer>
        {props.databaseQuery.accessAnnotation !=
          NoAdminDashboardDatabaseAccess && (
          <RunQueryCollapseContainer
            databaseQuery={props.databaseQuery}
            isOpen={isOpenRunQuery}
            tag={props.tag}
          />
        )}
      </Card>
      <br />
    </div>
  )
}
