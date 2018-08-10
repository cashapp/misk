import { IconName, IconNames } from "@blueprintjs/icons"
import axios from "axios"
import * as React from "react"
import { Helmet } from "react-helmet"
import { connect } from "react-redux"
import styled from "styled-components" 
import { IAppState, initialState } from "./"
import { ScriptComponent } from "./ScriptComponent"

interface ITabProps {
  children: any
  slug?: string
  hash: string
  loader: ILoaderState
  pathname: string
  search: string
}

export interface ILoaderState {
  adminTabs: {
    [key:string]: IAdminTab
  }
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

  sleep(ms: number) {
    return new Promise(resolve => setTimeout(resolve, ms))
  }

  async componentDidMount() {
    axios
    .get("http://localhost:8080/api/admintab/all")
    .then(response => {
      const adminTabs: { [key:string]: IAdminTab } = response.data
      console.log(adminTabs)
    })
  }

  render() {
    if (this.props.loader.adminTabs.tabname) {
      return (
        <Container>
          <h1>Loader Test</h1>
          
        </Container>
      )
    } else {
      return (
        <Container>
          <h1>Loader Test</h1>
          <ScriptComponent tab={this.props.loader.adminTabs.config}/>
          {/* {Object.entries(this.state.adminTabs).forEach(([key,tab]) => <ScriptComponent tab={tab}/>)} */}
          {Object.entries(this.props.loader.adminTabs).forEach(([key,tab]) => <p> {tab.name} {tab.icon} {tab.slug} {tab.url_path_prefix}</p>)}
        </Container>
      )
    }
  }
}

const mapStateToProps = (state: IAppState) => ({
  hash: state.router.location.hash,
  loader: state.loader,
  pathname: state.router.location.pathname,
  search: state.router.location.search,
})

export default connect(mapStateToProps)(Loader)
