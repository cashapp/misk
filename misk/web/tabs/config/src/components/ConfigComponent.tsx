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
    let final = ""
    let level = 0
    for (const c of json) {
      switch (c) {
        case "{": 
          level++
          break
        case "}":
          level--
          final += "\n" + this.indent(level)
          break
        case ",": 
          final += "\n" + this.indent(level)
          break
        case ":":
          // write parser for string
          final += ": "
          break
        case "\"":
          break
        case "\,":
          break
        default:
          final += c
      }
    }
    return(final)
  }

  oldToYaml(json: string) {
    return(json.split(":{").join(":\n \ \ ").split(",").join("\n").split("}").join("\n").split("{").join("").split('"').join(""))
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