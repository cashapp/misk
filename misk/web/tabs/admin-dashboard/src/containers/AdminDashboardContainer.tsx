import { Navbar, TabLoaderComponent, ResponsiveAppContainer } from "@misk/core"
import { simpleSelectorGet } from "@misk/simpleredux"
import * as React from "react"
import { connect } from "react-redux"
import { IDispatchProps, IState, rootDispatcher, rootSelectors } from "../ducks"
import { Spinner } from "@blueprintjs/core"

const tabsUrl = "/api/admindashboardtabs"
const serviceUrl = "/api/service/metadata"

class AdminDashboardContainer extends React.Component<IState & IDispatchProps> {
  async componentDidMount() {
    this.props.simpleHttpGet("adminDashboardTabs", tabsUrl)
    this.props.simpleHttpGet("serviceMetadata", serviceUrl)
  }

  render() {
    const adminDashboardTabs = simpleSelectorGet(this.props.simpleRedux, [
      "adminDashboardTabs",
      "data",
      "adminDashboardTabs"
    ])
    const serviceMetadata = simpleSelectorGet(this.props.simpleRedux, [
      "serviceMetadata",
      "data",
      "serviceMetadata"
    ])
    if (adminDashboardTabs != null && serviceMetadata) {
      return (
        <>
          <Navbar
            environment={serviceMetadata.environment}
            links={adminDashboardTabs}
            homeName={serviceMetadata.app_name}
            homeUrl={serviceMetadata.admin_dashboard_url}
            navbar_items={serviceMetadata.navbar_items}
            status={serviceMetadata.navbar_status}
          />
          <ResponsiveAppContainer>
            <TabLoaderComponent tabs={adminDashboardTabs} />
          </ResponsiveAppContainer>
        </>
      )
    } else {
      return (
        <>
          <Navbar />
          <ResponsiveAppContainer>
            <Spinner />
          </ResponsiveAppContainer>
        </>
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
)(AdminDashboardContainer)
