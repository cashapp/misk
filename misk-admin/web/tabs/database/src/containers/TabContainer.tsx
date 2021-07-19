import { simpleSelectorGet } from "@misk/simpleredux"
import * as React from "react"
import { connect } from "react-redux"
import { DatabaseContainer, TabHeader } from "../containers"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps
} from "../ducks"

const apiUrl = "/api/database/query/metadata"

class TabContainer extends React.Component<IState & IDispatchProps, IState> {
  componentDidMount() {
    this.props.simpleHttpGet("database-query", apiUrl)
  }
  render() {
    const metadata = simpleSelectorGet(this.props.simpleRedux, [
      "database-query",
      "data",
      "databaseQueryMetadata"
    ])

    return (
      <div>
        <TabHeader />
        <DatabaseContainer metadata={metadata} tag={"Database"} />
      </div>
    )
  }
}

export default connect(mapStateToProps, mapDispatchToProps)(TabContainer)
