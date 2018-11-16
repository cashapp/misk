import { IDashboardTab, Environment } from "@misk/common"
import {
  OfflineComponent,
  ResponsiveContainer,
  TopbarComponent
} from "@misk/components"
import * as React from "react"
import { connect } from "react-redux"
import { Route } from "react-router"
import { Link } from "react-router-dom"
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
  top: 110px;
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
        <span>
          <TopbarComponent
            environment={serviceMetadata.environment}
            links={adminDashboardTabs}
            homeName={serviceMetadata.app_name}
            homeUrl={serviceMetadata.admin_dashboard_url}
            navbarItems={[
              <Link key={3} to="/_admin/config/">
                Config
              </Link>,
              '<a href="#">Guice</>',
              <span key={2}>Search</span>
            ]}
            status={`${serviceMetadata.environment} | <a href="#">Guice</>`}
          />
          <TabContainer>
            {Object.entries(adminDashboardTabs).map(([key, tab]) => (
              <ScriptComponent key={key} tab={tab} />
            ))}
          </TabContainer>
        </span>
      )
    } else {
      return (
        <span>
          <TopbarComponent />
          <TabContainer>
            <OfflineComponent
              error={error}
              title={"Error Loading Multibound Admin Tabs"}
              endpoint={unavailableEndpointUrls}
            />
          </TabContainer>
        </span>
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
