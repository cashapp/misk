import {
  IDashboardTab,
  Navbar,
  OfflineComponent,
  ResponsiveContainer,
  TabLoaderComponent
} from "@misk/core"
import { simpleSelect } from "@misk/simpleredux"
import * as React from "react"
import { connect } from "react-redux"
import styled from "styled-components"
import { IDispatchProps, IState, rootDispatcher, rootSelectors } from "../ducks"

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

class LoaderContainer extends React.Component<IState & IDispatchProps> {
  async componentDidMount() {
    this.props.simpleNetworkGet("adminDashboardTabs", tabsUrl)
    this.props.simpleNetworkGet("serviceMetadata", serviceUrl)
  }

  render() {
    const adminDashboardTabs = simpleSelect(
      this.props.simpleNetwork,
      "adminDashboardTabs",
      "adminDashboardTabs"
    )
    const serviceMetadata = simpleSelect(
      this.props.simpleNetwork,
      "serviceMetadata",
      "serviceMetadata"
    )
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
          <Navbar
            environment={serviceMetadata.environment}
            links={adminDashboardTabs}
            homeName={serviceMetadata.app_name}
            homeUrl={serviceMetadata.admin_dashboard_url}
            navbar_items={serviceMetadata.navbar_items}
            status={serviceMetadata.navbar_status}
          />
          <TabContainer>
            <TabLoaderComponent tabs={adminDashboardTabs} />
          </TabContainer>
        </span>
      )
    } else {
      return (
        <span>
          <Navbar />
          <TabContainer>
            <OfflineComponent
              title={"Error Loading Multibound Admin Tabs"}
              endpoint={unavailableEndpointUrls}
            />
          </TabContainer>
        </span>
      )
    }
  }
}

const mapStateToProps = (state: IState) => rootSelectors(state)

const mapDispatchToProps = {
  ...rootDispatcher
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(LoaderContainer)
