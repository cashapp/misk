import { H1 } from "@blueprintjs/core"
import * as React from "react"
import { connect } from "react-redux"
import { DatabaseQueryContainer } from "../containers"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps
} from "../ducks"
import {
  IDatabaseQueryMetadataAPI,
  ServerTypes
} from "./DatabaseQueryInterfaces"

class TabContainer extends React.Component<IState & IDispatchProps, IState> {
  componentDidMount() {
    this.props.webActionsMetadata()
  }

  render() {
    const TEST_DATA: IDatabaseQueryMetadataAPI[] = [
      {
        allowedCapabilities: ["adrw", "maacosta"],
        allowedServices: ["clientsync", "cash-postmaster"],
        accessAnnotation: "TotallyLockedDownAccess",
        table: "movies",
        entityClass: "DbMovie",
        queryClass: "MovieQueries",
        constraints: {
          another: {
            fields: [
              {
                name: "name",
                repeated: false,
                type: ServerTypes.String
              }
            ]
          }
        },
        orders: {
          another: {
            fields: [
              {
                name: "name",
                repeated: false,
                type: ServerTypes.String
              }
            ]
          }
        },
        selects: {
          another: {
            fields: [
              {
                name: "name",
                repeated: false,
                type: ServerTypes.String
              }
            ]
          }
        }
      },
      {
        allowedCapabilities: ["adrw", "maacosta"],
        allowedServices: ["clientsync", "cash-postmaster"],
        accessAnnotation: "TotallyLockedDownAccess",
        table: "albums",
        entityClass: "DbAlbum",
        queryClass: "AlbumQueries",
        constraints: {
          another: {
            fields: [
              {
                name: "name",
                repeated: false,
                type: ServerTypes.String
              }
            ]
          }
        },
        orders: {
          another: {
            fields: [
              {
                name: "name",
                repeated: false,
                type: ServerTypes.String
              }
            ]
          }
        },
        selects: {
          another: {
            fields: [
              {
                name: "name",
                repeated: false,
                type: ServerTypes.String
              }
            ]
          }
        }
      }
    ]

    return (
      <div>
        <H1>Dashboard Query</H1>
        <DatabaseQueryContainer metadata={TEST_DATA} tag={"WebActions"} />
      </div>
    )
  }
}

export default connect(mapStateToProps, mapDispatchToProps)(TabContainer)
