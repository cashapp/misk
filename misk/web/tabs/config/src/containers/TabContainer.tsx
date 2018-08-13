// import { PathDebugComponent } from "@misk/components"
import * as React from "react"
import { Helmet } from "react-helmet"
import { connect } from "react-redux"
import styled from "styled-components" 
import { IAppState } from "../"
import { PathDebugComponent } from "../components"

interface ITabProps {
  children: any,
  slug?: string,
  hash: string
  pathname: string,
  search: string
}

const Container = styled.div`
  margin-left: 180px;
  margin-top: 20px;
`

class TabContainer extends React.Component<ITabProps, {children : any}> {
  constructor(props: ITabProps) {
    super(props)
  }

  render() {
    return (
      <Container>
        <div id={this.props.slug}/>
        {this.props.children}
        <PathDebugComponent hash={this.props.hash} pathname={this.props.pathname} search={this.props.search}/>
        <h1>Config</h1>
      </Container>
    )
  }
}

const mapStateToProps = (state: IAppState) => ({
  hash: state.router.location.hash,
  pathname: state.router.location.pathname,
  search: state.router.location.search,
})

export default connect(mapStateToProps)(TabContainer)
