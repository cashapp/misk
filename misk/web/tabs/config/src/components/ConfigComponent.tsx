// tslint:disable-next-line:no-var-requires
// const YAML = require("json2yaml")
import * as React from "react"
import styled from "styled-components"
import { IConfigFile } from "../containers/TabContainer"

interface IConfigProps {
  files: IConfigFile[]
  status: string
}

const Container = styled.div`
`

const ConfigOutput = styled.pre`
  font-family: Menlo, Fira Code;
`

export default class ConfigComponent extends React.PureComponent<IConfigProps> {
  indent(spaces: number) {
    let result = ""
    for (let i = 0; i < spaces; i++) {
      result += "\ "
    }
    return result
  }

  toYaml(json: string) {
    let result = ""
    let temp = ""
    let level = 0
    for (const c of json) {
      switch (c) {
        case "{": 
          level++
          break
        case "}":
          level--
          temp += "\n" + this.indent(level)
          break
        case ",": 
          temp += "\n" + this.indent(level)
          break
        case ":":
          // write parser for string
          temp += ": "
          break
        case "\"":
          break
        case "\,":
          break
        default:
          temp += c
      }
    }
    result += temp
    return(result)
  }

  oldToYaml(json: string) {
    return(json.split(":{").join(":\n \ \ ").split(",").join("\n").split("}").join("\n").split("{").join("").split("\"").join(""))
  }

  formattedFile(file: IConfigFile) {
    if (file.name === "live-config.yaml") {
      return this.toYaml(file.file)
    } else {
      return file.file
    }
  }
  
  render() {
    const { files, status } = this.props
    return(
      <Container>
        <h1>Config</h1>
        <p>{status}</p>
        {files && files.map(f => (
          <ConfigOutput>
            <h5><strong>classpath:/{f.name}</strong></h5>
            <code>{this.formattedFile(f)}</code>
          </ConfigOutput>))
        }
      </Container>
    )
  }
}