import { IMiskAdminTab, IMiskAdminTabs } from "@misk/common"
import { NavSidebarComponent, NavTopbarComponent, NoMatchComponent } from "@misk/components"
import { externals as MiskTabCommon } from "@misk/tabs" 
import * as React from "react"
import { connect } from "react-redux"
import { Route, Switch } from "react-router"
import { Link } from "react-router-dom"
import { dispatchLoader } from "../actions"
import { MountingDivComponent, ScriptComponent } from "../components"
import { ILoaderState, IState } from "../reducers"
import { IMultibinder } from "../utils/binder"
const Config = (window as any).MiskTabs.Config

interface IMiskTabs {
  [app:string]: any
}

export interface ILoaderProps {
  loader: ILoaderState
  cacheTabEntries: (MiskBinder: IMultibinder) => any
  getComponentsAndTabs: () => any
  getTabs: () => any
  getComponent: (tab: IMiskAdminTab) => any
  registerComponent: (name: string, Component: any) => any
}

class LoaderContainer extends React.Component<ILoaderProps> {
  async componentDidMount() {
    this.props.getTabs()
    setTimeout(this.props.cacheTabEntries, 1000, (window as any).MiskBinder)
  }

  buildTabRouteMountingDiv(key: any, tab: IMiskAdminTab) {
    return(<Route key={key} path={`/_admin/${tab.slug}`} render={() => <MountingDivComponent key={key} tab={tab}/>}/>)
  }

  render() {
    const { adminTabs, adminTabComponents, staleTabCache } = this.props.loader
    if (adminTabs) {
      const tabRouteDivs = Object.entries(adminTabs).map(([key,tab]) => this.buildTabRouteMountingDiv(key, tab))
      const tabLinks = Object.entries(adminTabs).map(([key,tab]) => <Link key={key} to={`/_admin/${tab.slug}`}>{tab.name}<br/></Link>)
      console.log(staleTabCache, (window as any).MiskBinders, adminTabs, adminTabComponents)
      return (
        <div>
          <NavTopbarComponent name="Misk Admin Loader" />
          <NavSidebarComponent adminTabs={adminTabs} />
          {Object.entries(adminTabs).map(([key,tab]) => (<ScriptComponent tab={tab}/>))}
          <Switch>
            {/* {Object.entries(adminTabs).map(([key,tab]) => this.buildTabRouteMountingDiv(key, tab))} */}
            {/* {Object.entries(adminTabs).map(([key,tab]) => this.buildTabRouteComponent(key, tab))} */}
            {/* {(window as any).MiskTabs && Object.entries(((window as any).MiskTabs as { [key:string]: { default: React.StatelessComponent }})).map(([key,Component]) => <Component key={key}/>)} */}
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
  cacheTabEntries: dispatchLoader.cacheTabEntries,
  getComponent: dispatchLoader.getOneComponent,
  getComponentsAndTabs: dispatchLoader.getAllComponentsAndTabs,
  getTabs: dispatchLoader.getAllTabs,
  registerComponent: dispatchLoader.registerComponent
}

export default connect(mapStateToProps, mapDispatchToProps)(LoaderContainer)
