import { H1 } from "@blueprintjs/core"
import * as React from "react"
import styled from "styled-components"

const Container = styled.div``

const ConfigEntry = styled.pre`
  font-family: Fira Code, Menlo;
`

const ConfigCode = styled.code`
  font-family: Fira Code, Menlo;
`

export interface IConfigResource {
  name: string
  file: string
}

export interface IConfigProps {
  resources: string
  status: string
}

export default class ConfigComponent extends React.PureComponent<IConfigProps> {
  renderConfig(resource: IConfigResource) {
    return (
      <ConfigEntry>
        <h5>
          <strong>{resource.name}</strong>
        </h5>
        <ConfigCode>{resource.file}</ConfigCode>
      </ConfigEntry>
    )
  }

  render() {
    const { resources, status } = this.props
    return (
      <Container>
        <H1>Config</H1>
        <p>{status}</p>
        {resources &&
          Object.entries(resources).map(([name, file]) =>
            this.renderConfig({ name, file })
          )}
      </Container>
    )
  }
}
