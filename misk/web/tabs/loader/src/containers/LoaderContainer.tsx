import { IMiskAdminTab, IMiskAdminTabs } from "@misk/common"
import { NavSidebarComponent, NavTopbarComponent, NoMatchComponent } from "@misk/components"
import { externals as MiskTabCommon } from "@misk/tabs" 
import { RouterState } from "connected-react-router"
import * as React from "react"
import { connect } from "react-redux"
import { Route, Switch } from "react-router"
import { Link } from "react-router-dom"
import { dispatchLoader } from "../actions"
import { MountingDivComponent, ScriptComponent } from "../components"
import { ILoaderState, IState } from "../reducers"
import { IMultibinder } from "../utils/binder"

interface IMiskTabs {
  [app:string]: any
}

export interface ILoaderProps {
  loader: ILoaderState
  router: RouterState
  cacheTabEntries: (MiskBinder: IMultibinder) => any
  getComponentsAndTabs: () => any
  getTabs: () => any
  getComponent: (tab: IMiskAdminTab) => any
  registerComponent: (name: string, Component: any) => any
}

class LoaderContainer extends React.Component<ILoaderProps> {
  async componentDidMount() {
    this.props.getTabs()
  }

  buildTabRouteMountingDiv(key: any, tab: IMiskAdminTab) {
    return(<Route key={key} path={`/_admin/${tab.slug}/`} render={() => <MountingDivComponent key={key} tab={tab}/>}/>)
  }

  render() {
    const { adminTabs, adminTabComponents, staleTabCache } = this.props.loader
    if (adminTabs) {
      const tabRouteDivs = Object.entries(adminTabs).map(([key,tab]) => this.buildTabRouteMountingDiv(key, tab))
      const tabLinks = Object.entries(adminTabs).map(([key,tab]) => <Link key={key} to={`/_admin/${tab.slug}/`}>{tab.name}<br/></Link>)
      console.log(staleTabCache, (window as any).MiskBinders, adminTabs, adminTabComponents)
      return (
        <div>
          <NavTopbarComponent name="Misk Admin Loader" />
          <NavSidebarComponent adminTabs={adminTabs} />
          {Object.entries(adminTabs).map(([key,tab]) => (<ScriptComponent tab={tab}/>))}
          <Switch>
            <Route component={NoMatchComponent}/>
          </Switch>
          <hr/>
          <h1>Loader Debug</h1>
          <Link to="/_admin/">Home</Link><br/>
          {tabLinks}
          <Link to="/_admin/asdf/asdf/asdf/asdf/">Bad Link</Link><br/>
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
  router: state.router
})

const mapDispatchToProps = {
  cacheTabEntries: dispatchLoader.cacheTabEntries,
  getComponent: dispatchLoader.getOneComponent,
  getComponentsAndTabs: dispatchLoader.getAllComponentsAndTabs,
  getTabs: dispatchLoader.getAllTabs,
  registerComponent: dispatchLoader.registerComponent
}

export default connect(mapStateToProps, mapDispatchToProps)(LoaderContainer)
