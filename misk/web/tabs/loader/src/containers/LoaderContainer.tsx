import { IMiskAdminTab, IMiskAdminTabs } from "@misk/common"
import { NoMatchComponent } from "@misk/components"
import * as React from "react"
import { connect } from "react-redux"
import { Route, Switch } from "react-router"
import { Link } from "react-router-dom"
import { dispatchLoader } from "../actions"
import { AllScriptsComponent, MountingDivComponent, ScriptComponent } from "../components"
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

  buildTabRouteMountingDiv(key: any, tab: IMiskAdminTab) {
    return(<Route key={key} path={`/_admin/test/${tab.slug}`} render={() => <MountingDivComponent key={key} tab={tab}/>}/>)
  }

  buildTabScripts(key: any, tab: IMiskAdminTab) {
    return(<Route key={key} path={`/_admin/test/${tab.slug}`} render={() => <MountingDivComponent key={key} tab={tab}/>}/>)
  }

  render() {
    const { adminTabs } = this.props.adminTabs
    const { adminTabComponents } = this.props.adminTabComponents
    if (adminTabs) {
      const tabRouteDivs = Object.entries(adminTabs).map(([key,tab]) => this.buildTabRouteMountingDiv(key, tab))
      const tabLinks = Object.entries(adminTabs).map(([key,tab]) => <Link key={key} to={`/_admin/test/${tab.slug}`}>{tab.name}<br/></Link>)
      console.log(adminTabs, adminTabComponents)
      console.log(tabRouteDivs)
      return (
        <div>
          <Switch>
          {/* <AllScriptsComponent tabs={adminTabs}/> */}
            {Object.entries(adminTabs).map(([key,tab]) => this.buildTabRouteMountingDiv(key, tab))}
            <Route component={NoMatchComponent}/>
          </Switch>
          <hr/>
          <h1>Loader Debug</h1>
          <Link to="/_admin/">Home</Link><br/>
          {tabLinks}
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
