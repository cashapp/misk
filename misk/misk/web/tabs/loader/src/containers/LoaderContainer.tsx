import { IconName } from "@blueprintjs/icons"
import * as React from "react"
import { connect } from "react-redux"
import { Route, Switch } from "react-router"
import { IAppState } from ".."
import { dispatchAdminTabs } from "../actions"
import { NoMatchComponent, ScriptComponent } from "../components"

interface ITabProps {
  adminTabs: IAdminTabs
  loading: boolean
  error: any
  getTabs: any
}

export interface ILoaderState {
  adminTabs: IAdminTabs
}

export interface IAdminTabs {
  [key:string]: IAdminTab
  toJS: any
}

export interface IAdminTab {
  icon: IconName
  name: string
  slug: string
  url_path_prefix: string
}

class LoaderContainer extends React.Component<ITabProps> {
  componentDidMount() {
    this.props.getTabs()
  }

  /**
   * 
   * @param tab [tabname: string, IAdminTab]
   * @returns React Router subroute for the tab that renders a ScriptComponent with the tab passed in as props
   */
  buildTabRouteComponent(tab: [string, IAdminTab]) {
    return(<Route key={tab[0]} path={`/_admin/test/${tab[1].slug}`} render={() => <ScriptComponent key={tab[0]} tab={tab[1]}/>}/>)
  }

  render() {
    const { adminTabs } = this.props.adminTabs
    if (adminTabs) {
      const tabRouteComponents = Object.entries(adminTabs).map((tab) => this.buildTabRouteComponent(tab))
      return (
        <div>
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
