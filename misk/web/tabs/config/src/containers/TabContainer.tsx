import { NoMatchComponent, PathDebugComponent } from "@misk/components"
import * as React from "react"
import { connect } from "react-redux"
import styled from "styled-components" 
import { dispatchConfig } from "../actions"
import { IState } from "../reducers"

interface ITabProps {
  children: any,
  config: {
    files: IConfigFile[]
    status: string
  }
  slug?: string,
  hash: string
  pathname: string,
  search: string,
  getConfigs: any
}

interface IConfigFile {
  name: string
  file: string
}

const Container = styled.div`
  margin-left: 180px;
  margin-top: 20px;
`

class TabContainer extends React.Component<ITabProps, {children : any}> {
  componentDidMount() {
    this.props.getConfigs()
  }

  render() {
    const { files } = this.props.config
    const { status } = this.props.config
    if (status) {
      return (
        <Container>
          <h1>App: Config</h1>
          <p>{status}</p>
  
          {files && files.map(f => (
            <div>
              <br/>
              <h5>{f.name}</h5>
              <code><pre>
                {f.file}
              </pre></code>
            </div>))}
        </Container>
      )
    } else {
      return (
        <Container>
          <p>Loading Config...</p>
        </Container>
      )
    }
  }
}

const mapStateToProps = (state: IState) => ({
  config: state.config.toJS(),
  hash: state.router.location.hash,
  pathname: state.router.location.pathname,
  search: state.router.location.search,
})

const mapDispatchToProps = {
  getConfigs: dispatchConfig.getAll,
}

export default connect(mapStateToProps, mapDispatchToProps)(TabContainer)
