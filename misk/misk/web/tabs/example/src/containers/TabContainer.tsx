import { OfflineComponent } from "@misk/components"
import * as React from "react"
import { connect } from "react-redux"
import { TabComponent } from "../components"
import { IComponentProps } from "../components/TabComponent"
import { dispatchExample, IState } from "../ducks"

interface IContainerProps {
  example: IComponentProps
  request: () => void
}

class TabContainer extends React.Component<IContainerProps, {children : any}> {
  componentDidMount() {
    this.props.request()
  }

  render() {
    const { data } = this.props.example
    if (data) {
      return (
        <TabComponent data={data} />
      )
    } else {
      return (
        <OfflineComponent title={"Error Loading Example Data"} endpoint={`/all example posts`}/>
      )
    }
  }
}

const mapStateToProps = (state: IState) => ({
  example: state.example.toJS(),
})

const mapDispatchToProps = {
  request: dispatchExample.request,
}

export default connect(mapStateToProps, mapDispatchToProps)(TabContainer)
