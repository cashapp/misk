import { chain } from "lodash"
import React, { useState } from "react"
import { connect } from "react-redux"
import { SkeletonDatabaseCardComponent } from "../components"
import {
  IDatabaseQueryMetadataAPI,
  FilterDatabaseContainer,
  DatabaseCardContainer,
} from "../containers"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps,
} from "../ducks"

const createTag = (databaseQuery: IDatabaseQueryMetadataAPI, tag: string) =>
  `${tag}::${databaseQuery.entityClass}::${databaseQuery.queryClass}`

const DatabaseContainer = (
  props: IState &
    IDispatchProps & {
      filterKey?: string
      filterValue?: string
      metadata?: IDatabaseQueryMetadataAPI[]
      tag: string
    }
) => {
  const metadata = props.metadata || []
  const [filteredMetadata, setFilteredMetadata] = useState(
    metadata as IDatabaseQueryMetadataAPI[]
  )

  if (metadata.length > 0) {
    return (
      <div>
        <FilterDatabaseContainer
          setFilteredMetadata={setFilteredMetadata}
          metadata={metadata}
          tag={props.tag}
        />
        <div>
          {/* {chain(filteredMetadata) */}
          {chain(filteredMetadata)
            .sortBy(["table", "queryClass"])
            .map((databaseQuery: IDatabaseQueryMetadataAPI, index: number) => (
              <DatabaseCardContainer
                databaseQuery={databaseQuery}
                key={index}
                tag={createTag(databaseQuery, "Database")}
              />
            ))}
        </div>
      </div>
    )
  } else {
    // Displays mock of 5 Database Query cards which fill in when data is available
    return (
      <div>
        <FilterDatabaseContainer
          setFilteredMetadata={() => undefined}
          disabled={true}
          metadata={[]}
          tag={props.tag}
        />
        <SkeletonDatabaseCardComponent />
        <br />
        <SkeletonDatabaseCardComponent />
        <br />
        <SkeletonDatabaseCardComponent />
        <br />
        <SkeletonDatabaseCardComponent />
        <br />
        <SkeletonDatabaseCardComponent />
        <br />
        <SkeletonDatabaseCardComponent />
      </div>
    )
  }
}

export default connect(mapStateToProps, mapDispatchToProps)(DatabaseContainer)
