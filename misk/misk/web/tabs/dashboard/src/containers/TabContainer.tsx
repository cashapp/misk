import { PathDebugComponent } from "@misk/components"
import * as React from "react"
import { connect } from "react-redux"
import { Link } from "react-router-dom"
import styled from "styled-components" 
import { IAppState } from "../"

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

const testLinks = (
  <div>
    <Link to="/_admin/dashboard/test1/1/test1">Test1</Link><br/>
    <Link to="/_admin/dashboard/test2/2/test2">Test2</Link><br/>
    <Link to="/_admin/dashboard/test3/3/test3">Test3</Link><br/>
    <Link to="/_admin/test3/3/test3">Home - Test2</Link><br/>
  </div>
)

class TabContainerClass extends React.Component<ITabProps, {children : any}> {
  constructor(props: ITabProps) {
    super(props)
  }

  render() {
    return (
      <Container>
        <div id={this.props.slug}/>
        {testLinks}
        <PathDebugComponent hash={this.props.hash} pathname={this.props.pathname} search={this.props.search}/>
        <hr/>
        {this.props.children}
      </Container>
    )
  }
}

const mapStateToProps = (state: IAppState) => ({
  hash: state.router.location.hash,
  pathname: state.router.location.pathname,
  search: state.router.location.search,
})

export const TabContainer = connect(mapStateToProps)(TabContainerClass)
