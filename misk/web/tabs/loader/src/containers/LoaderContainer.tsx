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
  getServiceMetadata: (url: string) => any
}

const TabContainer = styled(ResponsiveContainer)`
  position: relative;
  top: 100px;
  padding-left: 5px;
`

const tabsUrl = "/api/admintabs"
const serviceUrl = "/api/service/metadata"

class LoaderContainer extends React.Component<ILoaderProps> {
  async componentDidMount() {
    this.props.getTabs(tabsUrl)
    this.props.getServiceMetadata(serviceUrl)
  }

  buildTabRouteMountingDiv(tab: IMiskAdminTab) {
    return(<Route path={`/_admin/${tab.slug}/`} render={() => <MountingDivComponent tab={tab}/>}/>)
  }

  render() {
    const { adminTabs, serviceMetadata } = this.props.loader
    let unavailableEndpointUrls = ""
    if (!adminTabs) { unavailableEndpointUrls += tabsUrl + " " }
    if (!serviceMetadata) { unavailableEndpointUrls += serviceUrl + " " }
    if (adminTabs && serviceMetadata) {
      return (
        <div>
          <TopbarComponent links={adminTabs} serviceMetadata={serviceMetadata}/>
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
            <OfflineComponent title={"Error Loading Multibound Admin Tabs"} endpoint={unavailableEndpointUrls}/>
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
  getServiceMetadata: dispatchLoader.getServiceMetadata,
  getTabs: dispatchLoader.getAllTabs,
}

export default connect(mapStateToProps, mapDispatchToProps)(LoaderContainer)