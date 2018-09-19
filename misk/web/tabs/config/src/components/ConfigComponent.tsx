import * as React from "react"
import styled from "styled-components"
const json2yaml = require("json2yaml")
import { IConfigResource } from "../containers/TabContainer"

interface IConfigProps {
  resources: IConfigResource[]
  status: string
}

const Container = styled.div`
`

const ConfigOutput = styled.pre`
  font-family: Menlo, Fira Code;
`

export default class ConfigComponent extends React.PureComponent<IConfigProps> {
  renderConfig(resource: IConfigResource) {
    return(
      <ConfigOutput>
        <h5><strong>classpath:/{resource.name}</strong></h5>
        <code>{resource.name === "live-config.yaml" ? json2yaml.stringify(JSON.parse(resource.file)) : resource.file}</code>
      </ConfigOutput>
    )
  }
  
  render() {
    const { resources, status } = this.props
    return(
      <Container>
        <h1>Config</h1>
        <p>{status}</p>
        {resources && resources.map(resource => this.renderConfig(resource))}
      </Container>
    )
  }
}