import { simpleSelectorGet } from "@misk/simpleredux"
import { chain } from "lodash"
import React, { useState } from "react"
import { connect } from "react-redux"
import { SkeletonWebActionsComponent } from "../components"
import {
  IDatabaseQueryMetadataAPI,
  FilterDatabaseQueryContainer,
  DatabaseQueryCardContainer
} from "."
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps
} from "../ducks"

const createTag = (databaseQuery: IDatabaseQueryMetadataAPI, tag: string) =>
  `${tag}::${databaseQuery.queryClass}`

const DatabaseQueryContainer = (
  props: IState &
    IDispatchProps & {
      filterKey?: string
      filterValue?: string
      metadata?: IDatabaseQueryMetadataAPI[]
      tag: string
    }
) => {
  const metadata =
    props.metadata || simpleSelectorGet(props.webActions, "metadata", [])
  const [filteredMetadata, setFilteredMetadata] = useState(
    metadata as IDatabaseQueryMetadataAPI[]
  )
  if (metadata.length > 0) {
    return (
      <div>
        <FilterDatabaseQueryContainer
          setFilteredMetadata={setFilteredMetadata}
          metadata={metadata}
          tag={props.tag}
        />
        <div>
          {chain(filteredMetadata)
            .sortBy(["table", "queryClass"])
            .map((databaseQuery: IDatabaseQueryMetadataAPI, index: number) => (
              <DatabaseQueryCardContainer
                databaseQuery={databaseQuery}
                key={index}
                tag={createTag(databaseQuery, "DatabaseQuery")}
              />
            ))}
        </div>
      </div>
    )
  } else {
    // Displays mock of 5 Database Query cards which fill in when data is available
    return (
      <div>
        <FilterDatabaseQueryContainer
          setFilteredMetadata={setFilteredMetadata}
          disabled={true}
          metadata={[]}
          tag={props.tag}
        />
        {/* TODO (adrw) mock this to be Database Query */}
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

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(DatabaseQueryContainer)
