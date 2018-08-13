import { IconName, IconNames } from "@blueprintjs/icons"
import * as React from "react"
import { connect } from "react-redux"
import { Route, Switch } from "react-router"
import styled from "styled-components"
import { IAppState } from ".."
import { loader } from "../actions"
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

const Container = styled.div``

class LoaderContainer extends React.Component<ITabProps> {
  componentDidMount() {
    this.props.getTabs()
  }

  render() {
    const { adminTabs } = this.props.adminTabs
    if (adminTabs) {
      const tabRouteComponents = Object.entries(adminTabs).map((tab) => <Route key={tab[0]} path={`/_admin/test/${tab[1].slug}`} render={() => <ScriptComponent key={tab[0]} tab={tab[1]}/>}/>)
      return (
        <Container>
          <Switch>
            {tabRouteComponents}
            <Route component={NoMatchComponent}/>
          </Switch>
        </Container>
      )
    } else {
      return (
        <Container>
          <p>Loading Tabs...</p>
        </Container>
      )
    }
  }
}

const mapStateToProps = (state: IAppState) => ({
  adminTabs: state.adminTabs.toJS().data
})

const mapDispatchToProps = {
  getTabs: loader.getAdminTabs
}

export default connect(mapStateToProps, mapDispatchToProps)(LoaderContainer)
