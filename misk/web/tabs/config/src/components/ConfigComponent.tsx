import * as React from "react"
import styled from "styled-components"
import { IConfigResources, IConfigState } from "../ducks"

const Container = styled.div`
`

const ConfigOutput = styled.pre`
  font-family: Menlo, Fira Code;
`

export default class ConfigComponent extends React.PureComponent<IConfigState> {
  renderConfig(resource: IConfigResources) {
    return(
      <ConfigOutput>
        <h5><strong>{resource.name}</strong></h5>
        <code>{resource.file}</code>
      </ConfigOutput>
    )
  }
  
  render() {
    const { resources, status } = this.props
    return(
      <Container>
        <h1>Config</h1>
        <p>{status}</p>
        {resources && Object.entries(resources).map(([name,file]) => this.renderConfig({name, file}))}
      </Container>
    )
  }
}