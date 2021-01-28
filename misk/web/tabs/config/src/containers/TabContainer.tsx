import { simpleSelectorGet } from "@misk/simpleredux"
import * as React from "react"
import { connect } from "react-redux"
import { ConfigComponent } from "../components"
import { IDispatchProps, IState, rootDispatcher, rootSelectors } from "../ducks"

export interface IConfigResources {
  [name: string]: string
}

const apiUrl = "/api/config/metadata"

class TabContainer extends React.Component<IState & IDispatchProps, IState> {
  componentDidMount() {
    this.props.simpleHttpGet("config", apiUrl)
  }

  render() {
    const resources = simpleSelectorGet(this.props.simpleRedux, [
      "config",
      "data",
      "resources",
    ])
    return <ConfigComponent resources={resources} />
  }
}

const mapStateToProps = (state: IState) => rootSelectors(state)

const mapDispatchToProps = {
  ...rootDispatcher,
}

export default connect(mapStateToProps, mapDispatchToProps)(TabContainer)
