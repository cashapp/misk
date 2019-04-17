import { Card, Classes, H3 } from "@blueprintjs/core"
import { FlexContainer } from "@misk/core"
import { simpleSelect } from "@misk/simpleredux"
import { HTTPMethod } from "http-method-enum"
import chain from "lodash/chain"
import * as React from "react"
import { connect } from "react-redux"
import {
  CodeTag,
  Column,
  FloatLeft,
  Header,
  Metadata,
  MetadataMenu,
  MethodTag
} from "../components"
import {
  FilterWebActionsContainer,
  WebActionCardContainer
} from "../containers"
import {
  IDispatchProps,
  IState,
  IWebActionInternal,
  mapDispatchToProps,
  mapStateToProps,
  WebActionInternalLabel
} from "../ducks"

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
            content={<SkeletonText />}
          />
          <Metadata label={"Network Interceptors"} content={<SkeletonText />} />
          <Metadata label={"Send a Request"} content={<SkeletonText />} />
        </MetadataMenu>
      </Column>
    </FlexContainer>
  </Card>
)

const createTag = (action: IWebActionInternal, tag: string) =>
  `${tag}::${action.name}${action.pathPattern}`

const WebActionsContainer = (
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
        <FilterWebActionsContainer tag={props.tag} />
        <div>
          {(filteredMetadata as IWebActionInternal[]).map((action: any) => (
            <WebActionCardContainer
              action={action}
              tag={createTag(action, props.tag)}
            />
          ))}
        </div>
      </div>
    )
  } else {
    // Displays mock of 5 Web Action cards which fill in when data is available
    return (
      <div>
        <FilterWebActionsContainer {...props} />
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

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(WebActionsContainer)
