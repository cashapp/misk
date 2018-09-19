import { OfflineComponent } from "@misk/components"
import * as React from "react"
import { connect } from "react-redux"
import { ConfigComponent } from "../components"
import { dispatchConfig, IState } from "../ducks"

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

const apiUrl = "/api/config/all"

class TabContainer extends React.Component<ITabProps, {children : any}> {
  componentDidMount() {
    this.props.getAllConfig(apiUrl)
  }

  render() {
    const { resources, status } = this.props.config
      if (resources) {
      return (
        <ConfigComponent resources={resources} status={status} />
      )
    } else {
      return (
        <OfflineComponent title={"Error Loading Config Data"} endpoint={apiUrl}/>
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
