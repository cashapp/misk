// tslint:disable-next-line:no-var-requires
const YAML = require("json2yaml")
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
  indent(spaces: number) {
    let final = ""
    for (let i = 0; i < spaces; i++) {
      final += "\ "
    }
    return final
  }

  toYaml(json: string) {
    // tslint:disable-next-line:quotemark
    let final = ""
    let level = 0
    for (let i = 0; i > json.length; i++) {
      const c = json[i]
      switch (c) {
        case "{": 
          level++
          break
        case "}":
          level--
        case ",": 
          final += c + "\n" + this.indent(level)
        default:
          final += c
      }
    }
    return final
    // json.split(":{").join(":\n \ \ ").split(",").join("").split("}").join("\n").split("{").join("").split('"').join("")
    // return final
  }

  formattedFile(file: IConfigFile) {
    if (file.name === "live-config.yaml") {
      console.log("toYaml:", this.toYaml(file.file))
      return this.toYaml(file.file)
    } else {
      return file.file
    }
  }
  
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
              {this.formattedFile(f)}
            </pre></code>
          </div>))}
        </Container>
    )
  }
}