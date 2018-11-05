import { IDashboardTab } from "@misk/common"
import {
  OfflineComponent,
  ResponsiveContainer,
  TopbarComponent
} from "@misk/components"
import * as React from "react"
import { connect } from "react-redux"
import { Route } from "react-router"
import styled from "styled-components"
import { MountingDivComponent, ScriptComponent } from "../components"
import { dispatchLoader, IState } from "../ducks"

export interface ILoaderProps extends IState {
  getTabs: (url: string) => any
  getComponent: (tab: IDashboardTab) => any
  getServiceMetadata: (url: string) => any
}

const TabContainer = styled(ResponsiveContainer)`
  position: relative;
  top: 100px;
  padding-left: 5px;
`

const tabsUrl = "/api/admindashboardtabs"
const serviceUrl = "/api/service/metadata"

class LoaderContainer extends React.Component<ILoaderProps> {
  async componentDidMount() {
    this.props.getTabs(tabsUrl)
    this.props.getServiceMetadata(serviceUrl)
  }

  buildTabRouteMountingDiv(tab: IDashboardTab) {
    return (
      <Route
        path={`/_admin/${tab.slug}/`}
        render={() => <MountingDivComponent tab={tab} />}
      />
    )
  }

  render() {
    const { adminDashboardTabs, serviceMetadata, error } = this.props.loader
    let unavailableEndpointUrls = ""
    if (!adminDashboardTabs) {
      unavailableEndpointUrls += tabsUrl + " "
    }
    if (!serviceMetadata) {
      unavailableEndpointUrls += serviceUrl + " "
    }
    if (adminDashboardTabs && serviceMetadata) {
      return (
        <div>
          <TopbarComponent
            links={adminDashboardTabs}
            homeName={serviceMetadata.app_name}
            homeUrl={serviceMetadata.admin_dashboard_url}
          />
          <TabContainer>
            {Object.entries(adminDashboardTabs).map(([key, tab]) => (
              <ScriptComponent key={key} tab={tab} />
            ))}
          </TabContainer>
        </div>
      )
    } else {
      return (
        <div>
          <TopbarComponent />
          <TabContainer>
            <OfflineComponent
              error={error}
              title={"Error Loading Multibound Admin Tabs"}
              endpoint={unavailableEndpointUrls}
            />
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
  getTabs: dispatchLoader.getAllTabs
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(LoaderContainer)
