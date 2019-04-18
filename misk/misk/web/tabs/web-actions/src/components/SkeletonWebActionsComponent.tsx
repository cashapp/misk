import { Card, Classes, H3 } from "@blueprintjs/core"
import { FlexContainer } from "@misk/core"
import { HTTPMethod } from "http-method-enum"
import * as React from "react"
import {
  CodeTag,
  Column,
  FloatLeft,
  Header,
  Metadata,
  MetadataMenu,
  MethodTag
} from "."

/**
 * Empty Web Action Card UI for use with BlueprintJS Skeleton class in loading UIs
 * https://blueprintjs.com/docs/#core/components/skeleton
 */
export const SkeletonText = () => (
  <span className={Classes.SKELETON}>{"Lorem ipsum"}</span>
)

export const SkeletonWebActionsComponent = () => (
  <Card>
    <Header>
      {[HTTPMethod.GET].map((m, index) => (
        <MethodTag key={index} method={m} />
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
            content={<SkeletonText />}
          />
          <Metadata label={"Network Interceptors"} content={<SkeletonText />} />
          <Metadata label={"Send a Request"} content={<SkeletonText />} />
        </MetadataMenu>
      </Column>
    </FlexContainer>
  </Card>
)
