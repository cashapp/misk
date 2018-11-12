import * as React from "react"
import styled from "styled-components"
import { IConfigResources, IConfigState } from "../ducks"

const Container = styled.div``

const ConfigEntry = styled.pre`
  font-family: Fira Code, Menlo;
`

const ConfigCode = styled.code`
  font-family: Fira Code, Menlo;
`

export default class ConfigComponent extends React.PureComponent<IConfigState> {
  renderConfig(resource: IConfigResources) {
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
        <h1>Config</h1>
        <p>{status}</p>
        {resources &&
          Object.entries(resources).map(([name, file]) =>
            this.renderConfig({ name, file })
          )}
      </Container>
    )
  }
}
