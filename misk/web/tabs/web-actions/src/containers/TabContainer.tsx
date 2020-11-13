import { H1 } from "@blueprintjs/core"
import * as React from "react"
import { connect } from "react-redux"
import { WebActionsContainer } from "../containers"
import {
  IDispatchProps,
  IState,
  mapDispatchToProps,
  mapStateToProps
} from "../ducks"

class TabContainer extends React.Component<IState & IDispatchProps, IState> {
  componentDidMount() {
    this.props.webActionsMetadata()
  }

  render() {
    return (
      <div>
        <H1>Web Actions</H1>
        <WebActionsContainer tag={"WebActions"} />
      </div>
    )
  }
}

export default connect(mapStateToProps, mapDispatchToProps)(TabContainer)
