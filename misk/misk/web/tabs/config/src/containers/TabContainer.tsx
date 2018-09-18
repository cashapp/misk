import { OfflineComponent } from "@misk/components"
import * as React from "react"
import { connect } from "react-redux"
import { dispatchConfig } from "../actions"
import { ConfigComponent } from "../components"
import { IState } from "../reducers"

interface ITabProps {
  config: {
    resources: IConfigResource[]
    status: string
  }
  getAllConfig: (url: string) => void
}

export interface IConfigResource {
  name: string
  file: string
}

const configUrl = "/api/config/all"

class TabContainer extends React.Component<ITabProps, {children : any}> {
  componentDidMount() {
    this.props.getAllConfig(configUrl)
  }

  render() {
    const { resources, status } = this.props.config
      if (resources) {
      return (
        <ConfigComponent resources={resources} status={status} />
      )
    } else {
      return (
        <OfflineComponent title={"Error Loading Config Data"} endpoint={configUrl}/>
      )
    }
  }
}

const mapStateToProps = (state: IState) => ({
  config: state.config.toJS(),
})

const mapDispatchToProps = {
  getAllConfig: dispatchConfig.getAll,
}

export default connect(mapStateToProps, mapDispatchToProps)(TabContainer)
