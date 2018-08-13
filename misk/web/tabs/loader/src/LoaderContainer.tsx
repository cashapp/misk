import { IconName, IconNames } from "@blueprintjs/icons"
import axios from "axios"
import * as React from "react"
import { Helmet } from "react-helmet"
import { connect } from "react-redux"
import styled from "styled-components" 
import { IAppState } from "."
import { loader } from "./actions"
import { ScriptComponent } from "./ScriptComponent"

interface ITabProps {
  children: any
  slug?: string
  hash: string
  adminTabs: IAdminTabs
  pathname: string
  search: string
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
      const tabComponents = Object.entries(adminTabs).map((tab) => <ScriptComponent key={tab[0]} tab={tab[1]}/>)
      return (
        <Container>
          {tabComponents}
        </Container>
      )
    } else {
      return (
        <Container>
          <h1>Loading Tabs...</h1>
        </Container>
      )
    }
  }
}

const mapStateToProps = (state: IAppState) => ({
  adminTabs: state.adminTabs.toJS().data,
  hash: state.router.location.hash,
  pathname: state.router.location.pathname,
  search: state.router.location.search,
})

const mapDispatchToProps = {
  getTabs: loader.getAdminTabs
}

export default connect(mapStateToProps, mapDispatchToProps)(LoaderContainer)
