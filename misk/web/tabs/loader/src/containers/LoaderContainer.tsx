import { IMiskAdminTab, IMiskAdminTabs } from "@misk/common"
import { NoMatchComponent } from "@misk/components"
import * as React from "react"
import { connect } from "react-redux"
import { Route, Switch } from "react-router"
import { Link } from "react-router-dom"
import { dispatchLoader } from "../actions"
import { MountingDivComponent, NavSidebarComponent, NavTopbarComponent, ScriptComponent } from "../components"
import { IState } from "../reducers"

export interface ILoaderProps {
  loader: {
    adminTabComponents: any
    adminTabs: IMiskAdminTabs
  }
  getComponent: any
  getComponentsAndTabs: any
  getTabs: any
}

class LoaderContainer extends React.Component<ILoaderProps> {
  componentDidMount() {
    this.props.getComponentsAndTabs()
  }

  buildTabRouteMountingDiv(key: any, tab: IMiskAdminTab) {
    return(<Route key={key} path={`/_admin/test/${tab.slug}`} render={() => <MountingDivComponent key={key} tab={tab}/>}/>)
  }

  render() {
    const { adminTabs } = this.props.loader
    const { adminTabComponents } = this.props.loader
    if (adminTabs) {
      const tabRouteDivs = Object.entries(adminTabs).map(([key,tab]) => this.buildTabRouteMountingDiv(key, tab))
      const tabLinks = Object.entries(adminTabs).map(([key,tab]) => <Link key={key} to={`/_admin/${tab.slug}`}>{tab.name}<br/></Link>)
      console.log(adminTabs, adminTabComponents)
      return (
        <div>
          <NavTopbarComponent name="Misk Admin Loader" />
          <NavSidebarComponent adminTabs={adminTabs} />
          {Object.entries(adminTabs).map(([key,tab]) => (<ScriptComponent tab={tab}/>))}
          <Switch>
            {Object.entries(adminTabs).map(([key,tab]) => this.buildTabRouteMountingDiv(key, tab))}
            <Route component={NoMatchComponent}/>
          </Switch>
          <hr/>
          <h1>Loader Debug</h1>
          <Link to="/_admin/">Home</Link><br/>
          {tabLinks}
          <Link to="/_admin/asdf/asdf/asdf/asdf/">Bad Link</Link><br/>
          <p>Revert NavSideBar so it uses Link instead of A Href</p>
          <p>Next test that config actually keeps running with an updating timer from the last config</p>
          <p>Turn off their routing, maybe with the hack redirect javscript set it to hide when not on matching route?</p>
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
  loader: state.loader.toJS(),
})

const mapDispatchToProps = {
  getComponent: dispatchLoader.getOneComponent,
  getComponentsAndTabs: dispatchLoader.getAllComponentsAndTabs,
  getTabs: dispatchLoader.getAllTabs,
}

export default connect(mapStateToProps, mapDispatchToProps)(LoaderContainer)
