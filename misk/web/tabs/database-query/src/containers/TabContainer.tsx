import { H1 } from "@blueprintjs/core"
import * as React from "react"
import { connect } from "react-redux"
import { ServerTypes } from "src/form-builder"
import { DatabaseQueryContainer } from "../containers"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps
} from "../ducks"
import { IDatabaseQueryMetadataAPI } from "./DatabaseQueryInterfaces"

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
        types: {
          "constraint::name": {
            fields: [
              {
                name: "name",
                repeated: false,
                type: ServerTypes.String
              }
            ]
          },
          "constraint::releaseDateLessThan": {
            fields: [
              {
                name: "upperBound",
                repeated: false,
                // TODO change to LocalDate
                type: ServerTypes.String
              }
            ]
          },
          "order::releaseDateAsc": {
            fields: []
          },
          "order::releaseDateDesc": {
            fields: []
          },
          "select::listAsNameAndReleaseDate": {
            fields: []
          },
          "select::uniqueName": {
            fields: []
          },
          "select::listAsNames": {
            fields: []
          }
        },
        constraints: [
          {
            name: "name",
            parametersType: "constraint::name",
            path: "name"
          },
          {
            name: "releaseDateLessThan",
            parametersType: "constraint::releaseDateLessThan",
            path: "release_date",
            operator: "LT"
          }
        ],
        orders: [
          {
            name: "releaseDateAsc",
            parametersType: "order::releaseDateAsc",
            path: "release_date",
            ascending: false
          },
          {
            name: "releaseDateDesc",
            parametersType: "order::releaseDateDesc",
            path: "release_date",
            ascending: true
          }
        ],
        selects: [
          {
            name: "listAsNameAndReleaseDate",
            parametersType: "select::listAsNameAndReleaseDate",
            paths: ["name", "release_date"]
          },
          {
            name: "uniqueName",
            parametersType: "select::uniqueName",
            paths: ["name"]
          },
          {
            name: "listAsNames",
            parametersType: "select::listAsNames",
            paths: ["name"]
          }
        ]
      },
      {
        allowedCapabilities: ["adrw", "maacosta"],
        allowedServices: ["clientsync", "cash-postmaster"],
        accessAnnotation: "TotallyLockedDownAccess",
        table: "albums",
        entityClass: "DbAlbum",
        queryClass: "AlbumQueries",
        types: {
          "constraint::name": {
            fields: [
              {
                name: "name",
                repeated: false,
                type: ServerTypes.String
              }
            ]
          },
          "constraint::releaseDateLessThan": {
            fields: [
              {
                name: "upperBound",
                repeated: false,
                // TODO change to LocalDate
                type: ServerTypes.String
              }
            ]
          },
          "order::releaseDateAsc": {
            fields: []
          },
          "order::releaseDateDesc": {
            fields: []
          },
          "select::listAsNameAndReleaseDate": {
            fields: []
          },
          "select::uniqueName": {
            fields: []
          },
          "select::listAsNames": {
            fields: []
          }
        },
        constraints: [
          {
            name: "name",
            parametersType: "constraint::name",
            path: "name"
          },
          {
            name: "releaseDateLessThan",
            parametersType: "constraint::releaseDateLessThan",
            path: "release_date",
            operator: "LT"
          }
        ],
        orders: [
          {
            name: "releaseDateAsc",
            parametersType: "order::releaseDateAsc",
            path: "release_date",
            ascending: false
          },
          {
            name: "releaseDateDesc",
            parametersType: "order::releaseDateDesc",
            path: "release_date",
            ascending: true
          }
        ],
        selects: [
          {
            name: "listAsNameAndReleaseDate",
            parametersType: "select::listAsNameAndReleaseDate",
            paths: ["name", "release_date"]
          },
          {
            name: "uniqueName",
            parametersType: "select::uniqueName",
            paths: ["name"]
          },
          {
            name: "listAsNames",
            parametersType: "select::listAsNames",
            paths: ["name"]
          }
        ]
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
