import { IMiskAdminTab, IMiskAdminTabs } from "@misk/common"
import * as React from "react"
import { connect } from "react-redux"
import { Route, Switch } from "react-router"
import { Link } from "react-router-dom"
import { dispatchLoader } from "../actions"
import { NoMatchComponent, ScriptComponent } from "../components"
import { IState } from "../reducers"

interface ITabProps {
  adminTabComponents: any
  adminTabs: IMiskAdminTabs
  loadableTabs: any
  loading: boolean
  error: any
  getComponent: any
  getComponents: any
  getTabs: any
}

export interface ILoaderState {
  adminTabs: IMiskAdminTabs
}

class LoaderContainer extends React.Component<ITabProps> {
  componentDidMount() {
    this.props.getComponents()
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
    const { adminTabComponents } = this.props.adminTabComponents
    if (adminTabs) {
      const tabRouteComponents = Object.entries(adminTabs).map(([key,tab]) => this.buildTabRouteComponent(tab))
      const tabLinks = Object.entries(adminTabs).map(([key,tab]) => <Link key={key} to={`/_admin/test/${tab.slug}`}>{tab.name}<br/></Link>)
      console.log(adminTabs, adminTabComponents)
      return (
        <div>
          <Link to="/_admin/">Home</Link><br/>
          {tabLinks}
          <div id="dashboard">
            <p>test</p>
          </div>
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

const mapStateToProps = (state: IState) => ({
  adminTabComponents: state.loader.toJS(),
  adminTabs: state.loader.toJS(),
})

const mapDispatchToProps = {
  getComponent: dispatchLoader.getOneComponent,
  getComponents: dispatchLoader.getAllComponentsAndTabs,
  getTabs: dispatchLoader.getAllTabs,
}

export default connect(mapStateToProps, mapDispatchToProps)(LoaderContainer)
