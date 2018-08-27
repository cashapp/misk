import * as React from "react"
import styled from "styled-components"
import { IConfigFile } from "../containers/TabContainer"

interface IConfigProps {
  files: IConfigFile[]
  status: string
}

const Container = styled.div`
  margin-left: 180px;
  margin-top: 20px;
`

export default class ConfigComponent extends React.PureComponent<IConfigProps> {
  render() {
    const { files, status } = this.props
    return(
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
  }
}