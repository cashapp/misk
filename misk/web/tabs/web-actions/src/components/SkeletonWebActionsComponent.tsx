/** @jsx jsx */
import { Card, Classes, H3, Menu, Tag } from "@blueprintjs/core"
import { jsx } from "@emotion/core"
import { FlexContainer } from "@misk/core"
import { HTTPMethod } from "http-method-enum"
import {
  cssCodeTag,
  cssColumn,
  cssFloatLeft,
  cssHeader,
  Metadata,
  MethodTag
} from "."
import { cssMetadataMenu } from "./CommonComponents"

/**
 * Empty Web Action Card UI for use with BlueprintJS Skeleton class in loading UIs
 * https://blueprintjs.com/docs/#core/components/skeleton
 */
export const SkeletonText = () => (
  <span className={Classes.SKELETON}>{"Lorem ipsum"}</span>
)

export const SkeletonWebActionsComponent = () => (
  <Card>
    <div css={cssHeader}>
      {[HTTPMethod.GET].map((m, index) => (
        <MethodTag key={index} method={m} />
      ))}
      <H3 css={cssFloatLeft} className={Classes.SKELETON}>
        {"AnotherSimpleWebAction"}
      </H3>
      <Tag css={[cssFloatLeft, cssCodeTag]} large={true}>
        {<SkeletonText />}
      </Tag>
    </div>
    <FlexContainer>
      <div css={cssColumn}>
        <Menu css={cssMetadataMenu}>
          <Metadata label={"Function"} content={<SkeletonText />} />
          <Metadata label={"Services"} content={<SkeletonText />} />
          <Metadata label={"Roles"} content={<SkeletonText />} />
          <Metadata label={"Access"} content={<SkeletonText />} />
        </Menu>
      </div>
      <div css={cssColumn}>
        <Menu css={cssMetadataMenu}>
          <Metadata label={"Content Types"} content={<SkeletonText />} />
          <Metadata
            label={"Application Interceptors"}
            content={<SkeletonText />}
          />
          <Metadata label={"Network Interceptors"} content={<SkeletonText />} />
          <Metadata label={"Send a Request"} content={<SkeletonText />} />
        </Menu>
      </div>
    </FlexContainer>
  </Card>
)
