import { Button, Collapse } from "@blueprintjs/core"
import axios from "axios"
// tslint:disable-next-line:no-var-requires
const dayjs = require("dayjs")
import React from "react"
import ReactJson from "react-json-view"
import Layouts from "../../layouts"

// potentially have bool array for the collapse-ibles and accept array of text as props

class RootContainer extends React.Component {
  public state = {
    config: {
      data: {
        effective_config: "",
        yaml_files: {
          "urlshortener-common.yaml": ""
        }
      },
      files: [{
        file: "",
        name: ""
      }]
    },
    isOpen: false,
    lastOnline: "",
    status: "Loading..."
  }

  sleep(ms: number) {
    return new Promise(resolve => setTimeout(resolve, ms))
  }

  toYaml(json: string) {
    return json.split(":{").join(":\n\t").split(",").join("\n").split("}").join("").split("{").join("")
  }

  async componentDidMount() {
    for (;;) {
      axios
      .get("http://localhost:8080/_admin/api/config/all")
      .then(response => {
        const data = response.data
        const files: any = []
        files.push({name: "live-config.yaml", file: this.toYaml(data.effective_config)})
        Object.entries(data.yaml_files).forEach(([key,value]) => {
          files.push({name: key, file: value})
        })
        const newState = {...this.state, 
          config: { data, files },
          lastOnline: dayjs().format("YYYY-MM-DD HH:mm:ss:SSS"),
          status: `Online as of: ${dayjs().format("YYYY-MM-DD HH:mm:ss:SSS")}`
        }
        this.setState(newState)
        // console.log(this.state)
      })
      .catch(err => {
        const files: any = [{name: "error: server offline, failed to retrieve config yamls", file: this.toYaml(JSON.stringify(err))}]
        const newState = {...this.state, 
          config: { files },
          status: `Offline since ${this.state.lastOnline}. Last ping ${dayjs().format("YYYY-MM-DD HH:mm:ss:SSS")}`
        }
        this.setState(newState)
      })
      await this.sleep(10)
    }

    
  }

  render() {
    return (
      <Layouts>
        <h1>App: Config</h1>
        <p>{this.state.status}</p>

        {this.state.config.files.map(f => (
          <div>
            <br/>
            <h5>{f.name}</h5>
            <code><pre>
              {f.file}
            </pre></code>
          </div>))}



        {/* {for x in x.keys this.state.config.data.yaml_files.keys (({yaml_name="yaml_name", yaml_payload="payload"} : any) => (
          <h5>{yaml_name}</h5>
          <Button onClick={this.handleClick}>
            {this.state.isOpen ? "Hide" : "Show"} build logs
          </Button>
          <Collapse isOpen={this.state.isOpen}>
            <pre><code>
                {JSON.stringify(yaml_payload, null, 2)}
            </code></pre>
          </Collapse>  
        ))} */}

        {/* <h5>Resolved Datasource JDBC URLs</h5>
        <h5>P2 Datasource Config</h5>
        <h5>P2 Config (overlayed into App Config)</h5>
        <h5>Environment</h5> */}
      </Layouts>
    )
  }

  private handleClick = () => {
    this.setState({ isOpen: !this.state.isOpen })
  }
}

export default RootContainer
