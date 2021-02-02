import { simpleSelectorGet } from "@misk/simpleredux"
import { chain } from "lodash"
// todo good test of render is change this to the below. it compiles but fails in browser
// import chain from "lodash/chain"
import * as React from "react"
import { connect } from "react-redux"
import { SkeletonWebActionsComponent } from "../components"
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

const createTag = (action: IWebActionInternal, tag: string) =>
  `${tag}::${action.name}${action.pathPattern}`

const filterMetadata = (
  metadata: IWebActionInternal[],
  filterKey: string,
  filterValue: string
): any =>
  chain(metadata)
    .filter((action: IWebActionInternal) =>
      ((action as any)[filterKey] || "")
        .toString()
        .toLowerCase()
        .includes(filterValue.toLowerCase())
    )
    .value()

const WebActionsContainer = (
  props: IState &
    IDispatchProps & {
      filterKey?: string
      filterValue?: string
      metadata?: IWebActionInternal[]
      tag: string
    }
) => {
  const metadata =
    props.metadata || simpleSelectorGet(props.webActions, "metadata", [])
  const filterTag = `${props.tag}::Filter`
  if (metadata.length > 0) {
    const filterKey =
      props.filterKey ||
      WebActionInternalLabel[
        simpleSelectorGet(props.simpleRedux, [
          `${filterTag}::HTMLSelect`,
          "data"
        ]) || "All Metadata"
      ]
    const filterValue = simpleSelectorGet(
      props.simpleRedux,
      [`${filterTag}::Input`, "data"],
      ""
    )
    return (
      <div>
        <FilterWebActionsContainer tag={props.tag} />
        <div>
          {filterMetadata(metadata, filterKey, filterValue).map(
            (action: any, index: number) => (
              <WebActionCardContainer
                action={action}
                key={index}
                tag={createTag(action, props.tag)}
              />
            )
          )}
        </div>
      </div>
    )
  } else {
    // Displays mock of 5 Web Action cards which fill in when data is available
    return (
      <div>
        <FilterWebActionsContainer disabled={true} tag={props.tag} />
        <SkeletonWebActionsComponent />
        <br />
        <SkeletonWebActionsComponent />
        <br />
        <SkeletonWebActionsComponent />
        <br />
        <SkeletonWebActionsComponent />
        <br />
        <SkeletonWebActionsComponent />
      </div>
    )
  }
}

export default connect(mapStateToProps, mapDispatchToProps)(WebActionsContainer)
