import axios from "axios"
import * as React from "react"
import { Helmet } from "react-helmet"
import { connect } from "react-redux"
import styled from "styled-components" 
import { IAppState } from "./"

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

class Loader extends React.Component<ITabProps, {children : any}> {
  constructor(props: ITabProps) {
    super(props)
  }

  async componentDidMount() {
    // axios
    // .get("http://localhost:8080/api/admintab/all")
    // .then(response => {

    // })
  }

  render() {
    return (
      <Container>
        <Helmet>
          <script src={`/_admin/${this.props.slug}/tab_${this.props.slug}.js`} type="text/javascript" />
        </Helmet>
        <div id={this.props.slug}/>
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

export default connect(mapStateToProps)(Loader)
