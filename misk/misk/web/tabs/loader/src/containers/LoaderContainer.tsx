import { IMiskAdminTab, IMiskAdminTabs } from "@misk/common"
import * as React from "react"
import { connect } from "react-redux"
import { Route, Switch } from "react-router"
import { Link } from "react-router-dom"
import { IAppState } from ".."
import { dispatchAdminTabs } from "../actions"
import { NoMatchComponent, ScriptComponent } from "../components"

interface ITabProps {
  adminTabs: IMiskAdminTabs
  loading: boolean
  error: any
  getTabs: any
}

export interface ILoaderState {
  adminTabs: IMiskAdminTabs
}

class LoaderContainer extends React.Component<ITabProps> {
  componentDidMount() {
    this.props.getTabs()
  }

  /**
   * 
   * @param tab [tabname: string, IMiskAdminTab]
   * @returns React Router subroute for the tab that renders a ScriptComponent with the tab passed in as props
   */
  buildTabRouteComponent(tab: IMiskAdminTab) {
    return(<Route key={tab.slug} path={`/_admin/test/${tab.slug}`} render={() => <ScriptComponent key={tab.slug} tab={tab}/>}/>)
  }

  render() {
    const { adminTabs } = this.props.adminTabs
    if (adminTabs) {
      const tabRouteComponents = Object.entries(adminTabs).map(([key,tab]) => this.buildTabRouteComponent(tab))
      const tabLinks = Object.entries(adminTabs).map(([key,tab]) => <Link key={key} to={`/_admin/test/${tab.slug}`}>{tab.name}</Link>)
      return (
        <div>
          <Link to="/_admin/">Home</Link><br/>
          {tabLinks}
          <Switch>
            {tabRouteComponents}
            <Route component={NoMatchComponent}/>
          </Switch>
        </div>
      )
    } else {
      return (
        <div>
          <p>Loading Tabs...</p>
        </div>
      )
    }
  }
}

const mapStateToProps = (state: IAppState) => ({
  adminTabs: state.adminTabs.toJS().data
})

const mapDispatchToProps = {
   getTabs: dispatchAdminTabs.getAll
}

export default connect(mapStateToProps, mapDispatchToProps)(LoaderContainer)
