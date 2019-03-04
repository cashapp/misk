import { Spinner } from "@blueprintjs/core"
import { simpleSelect } from "@misk/simpleredux"
import * as React from "react"
import { connect } from "react-redux"
import { ConfigComponent } from "../components"
import { IDispatchProps, IState, rootDispatcher, rootSelectors } from "../ducks"

export interface IConfigResources {
  [name: string]: string
}

const apiUrl = "/api/config/all"

class TabContainer extends React.Component<IState & IDispatchProps, IState> {
  componentDidMount() {
    this.props.simpleNetworkGet("config", apiUrl)
  }

  render() {
    const { resources, status } = simpleSelect(
      this.props.simpleNetwork,
      "config"
    )
    if (resources) {
      return <ConfigComponent resources={resources} status={status} />
    } else {
      return <Spinner />
    }
  }
}

const mapStateToProps = (state: IState) => rootSelectors(state)

const mapDispatchToProps = {
  ...rootDispatcher
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(TabContainer)
