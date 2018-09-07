import { NonIdealState } from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { IMiskAdminTab } from "@misk/common"
import { NavTopbarComponent, ResponsiveContainer } from "@misk/components"
import { RouterState } from "connected-react-router"
import * as React from "react"
const { Code } = require("react-content-loader")
import { connect } from "react-redux"
import { Route } from "react-router"
import styled from "styled-components"
import { dispatchLoader } from "../actions"
import { MountingDivComponent, ScriptComponent } from "../components"
import { ILoaderState, IState } from "../reducers"
import { IMultibinder } from "../utils/binder"

export interface ILoaderProps {
  loader: ILoaderState
  router: RouterState
  cacheTabEntries: (MiskBinder: IMultibinder) => any
  getComponentsAndTabs: () => any
  getTabs: () => any
  getComponent: (tab: IMiskAdminTab) => any
  registerComponent: (name: string, Component: any) => any
}

const TabContainer = styled(ResponsiveContainer)`
  position: relative;
  top: 100px;
  padding-left: 5px;
`

class LoaderContainer extends React.Component<ILoaderProps> {
  async componentDidMount() {
    this.props.getTabs()
  }

  buildTabRouteMountingDiv(tab: IMiskAdminTab) {
    return(<Route path={`/_admin/${tab.slug}/`} render={() => <MountingDivComponent tab={tab}/>}/>)
  }

  render() {
    const { adminTabs } = this.props.loader
    if (adminTabs) {
      return (
        <div>
          <NavTopbarComponent homeName="Misk" homeUrl="/_admin/" links={adminTabs}/>
          <TabContainer>
            {Object.entries(adminTabs).map(([key,tab]) => (<ScriptComponent key={key} tab={tab}/>))}
          </TabContainer>
        </div>
      )
    } else {
      return (
        <div>
          <NavTopbarComponent homeName="Misk" homeUrl="/_admin/"/>
          <TabContainer>
            <NonIdealState children={Code} icon={IconNames.OFFLINE} description="Loading tabs..."/>
          </TabContainer>
        </div>
      )
    }
  }
}

const mapStateToProps = (state: IState) => ({
  loader: state.loader.toJS(),
  router: state.router
})

const mapDispatchToProps = {
  cacheTabEntries: dispatchLoader.cacheTabEntries,
  getComponent: dispatchLoader.getOneComponent,
  getComponentsAndTabs: dispatchLoader.getAllComponentsAndTabs,
  getTabs: dispatchLoader.getAllTabs,
  registerComponent: dispatchLoader.registerComponent
}

export default connect(mapStateToProps, mapDispatchToProps)(LoaderContainer)