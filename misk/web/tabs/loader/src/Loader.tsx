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
  loader: ILoaderState
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
}

export interface IAdminTab {
  icon: IconName
  name: string
  slug: string
  url_path_prefix: string
}

const Container = styled.div``

class Loader extends React.Component<ITabProps> {
  // public state: ILoaderState = {
  //   adminTabs: {
  //     tabname: {
  //       icon: IconNames.WIDGET_BUTTON,
  //       name: "name",
  //       slug: "slug",
  //       url_path_prefix: "url_path_prefix",
  //     },
  //   }
  // } 

  constructor(props: ITabProps) {
    super(props)
  }

  componentDidMount() {
    // axios
    // .get("http://localhost:8080/api/admintab/all")
    // .then(response => {
    //   const adminTabs: { [key:string]: IAdminTab } = response.data
    //   console.log(adminTabs)
    // })
    this.props.getTabs()
  }

  render() {
    const { adminTabs } = this.props.adminTabs
    console.log(adminTabs)
    if (adminTabs) {
      return (
        <Container>
          <h1>Loader Test</h1>
          {/* {Object.entries(this.state.adminTabs).forEach(([key,tab]) => <ScriptComponent tab={tab}/>)} */}
          {Object.entries(adminTabs).forEach(([key,tab]) => <p> {tab.name} {tab.icon} {tab.slug} {tab.url_path_prefix}</p>)}
        </Container>
      )
    } else {
      return (
        <Container>
          <h1>Loader Test...</h1>
        </Container>
      )
    }
  }
}

const mapStateToProps = (state: any) => ({
  adminTabs: state.adminTabs.toJS().data,
  hash: state.router.location.hash,
  loader: state.loader,
  pathname: state.router.location.pathname,
  search: state.router.location.search,
})

const mapDispatchToProps = {
  getTabs: loader.getAdminTabs
}

export default connect(mapStateToProps, mapDispatchToProps)(Loader)
