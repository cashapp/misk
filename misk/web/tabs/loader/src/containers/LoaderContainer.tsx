import { NonIdealState } from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { IMiskAdminTab } from "@misk/common"
import { TopbarComponent, ResponsiveContainer } from "@misk/components"
import { RouterState } from "connected-react-router"
import * as React from "react"
const { Code } = require("react-content-loader")
import { connect } from "react-redux"
import { Route } from "react-router"
import styled from "styled-components"
import { dispatchLoader } from "../actions"
import { MountingDivComponent, ScriptComponent } from "../components"
import { ILoaderState, IState } from "../reducers"

export interface ILoaderProps {
  loader: ILoaderState
  router: RouterState
  cacheTabEntries: (MiskBinder: any) => any
  getComponentsAndTabs: (url: string) => any
  getTabs: (url: string) => any
  getComponent: (tab: IMiskAdminTab) => any
  registerComponent: (name: string, Component: any) => any
}

const TabContainer = styled(ResponsiveContainer)`
  position: relative;
  top: 100px;
  padding-left: 5px;
`

const adminTabsUrl = "/api/admintabs"

class LoaderContainer extends React.Component<ILoaderProps> {
  async componentDidMount() {
    this.props.getTabs(adminTabsUrl)
  }

  buildTabRouteMountingDiv(tab: IMiskAdminTab) {
    return(<Route path={`/_admin/${tab.slug}/`} render={() => <MountingDivComponent tab={tab}/>}/>)
  }

  render() {
    const { adminTabs, adminTabCategories } = this.props.loader
    if (adminTabs) {
      return (
        <div>
          <TopbarComponent homeName="URL Shortener" homeUrl="/_admin/" links={adminTabCategories} menuButtonShow={true}/>
          <TabContainer>
            {Object.entries(adminTabs).map(([key,tab]) => (<ScriptComponent key={key} tab={tab}/>))}
          </TabContainer>
        </div>
      )
    } else {
      return (
        <div>
          <TopbarComponent homeName="Misk" homeUrl="/_admin/" menuButtonShow={false}/>
          <TabContainer>
            <NonIdealState 
              icon={IconNames.OFFLINE} 
              title="Error Loading Tabs" 
              description={`Unable to get list of tabs from server to begin dashbaord render. Server endpoint '${adminTabsUrl}' is unavailable.`}
            >
            <Code/>
            </NonIdealState>
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