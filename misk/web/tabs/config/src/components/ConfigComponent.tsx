import { H1, H3 } from "@blueprintjs/core"
import { CodePreContainer } from "@misk/core"
import * as React from "react"
import styled from "styled-components"

const FileName = styled(H3)`
  font-family: Fira Code, Menlo;
`

export interface IConfigResource {
  name: string
  file: string
}

export interface IConfigProps {
  resources: string
}

export default class ConfigComponent extends React.PureComponent<IConfigProps> {
  renderConfig(resource: IConfigResource) {
    return (
      <div>
        <br />
        <FileName>{resource.name}</FileName>
        <CodePreContainer>{resource.file}</CodePreContainer>
      </div>
    )
  }

  render() {
    const { resources } = this.props
    return (
      <div>
        <H1>Config</H1>
        {resources &&
          Object.entries(resources).map(([name, file]) =>
            this.renderConfig({ name, file })
          )}
      </div>
    )
  }
}
