import { IMiskAdminTab } from "@misk/common"
import { OfflineComponent, ResponsiveContainer, TopbarComponent } from "@misk/components"
import { RouterState } from "connected-react-router"
import * as React from "react"
import { connect } from "react-redux"
import { Route } from "react-router"
import styled from "styled-components"
import { MountingDivComponent, ScriptComponent } from "../components"
import { dispatchLoader, ILoaderState, IState } from "../ducks"

export interface ILoaderProps {
  loader: ILoaderState
  router: RouterState
  getTabs: (url: string) => any
  getComponent: (tab: IMiskAdminTab) => any
}

const TabContainer = styled(ResponsiveContainer)`
  position: relative;
  top: 100px;
  padding-left: 5px;
`

const apiUrl = "/api/admintabs"

class LoaderContainer extends React.Component<ILoaderProps> {
  async componentDidMount() {
    this.props.getTabs(apiUrl)
  }

  buildTabRouteMountingDiv(tab: IMiskAdminTab) {
    return(<Route path={`/_admin/${tab.slug}/`} render={() => <MountingDivComponent tab={tab}/>}/>)
  }

  render() {
    const { adminTabs } = this.props.loader
    if (adminTabs) {
      return (
        <div>
          <TopbarComponent links={adminTabs}/>
          <TabContainer>
            {Object.entries(adminTabs).map(([key,tab]) => (<ScriptComponent key={key} tab={tab}/>))}
          </TabContainer>
        </div>
      )
    } else {
      return (
        <div>
          <TopbarComponent/>
          <TabContainer>
            <OfflineComponent title={"Error Loading Multibound Admin Tabs"} endpoint={apiUrl}/>
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
  getComponent: dispatchLoader.getOneComponent,
  getTabs: dispatchLoader.getAllTabs,
}

export default connect(mapStateToProps, mapDispatchToProps)(LoaderContainer)