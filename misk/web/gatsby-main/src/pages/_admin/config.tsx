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
        file: "Server offline...",
        name: "config.yaml waiting to update"
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
    // tslint:disable-next-line:quotemark
    return json.split(":{").join(":\n\ \ ").split(",").join("\n").split("}").join("").split("{").join("").split('"').join("")
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
          status: `Online as of: ${dayjs().format("YYYY-MM-DD HH:mm:ss")}`
        }
        this.setState(newState)
        // console.log(this.state)
      })
      // .catch(err => {
      //   const files: any = [{name: "error: server offline, failed to retrieve config yamls", file: this.toYaml(JSON.stringify(err))}]
      //   const newState = {...this.state,
      //     config: { files },
      //     status: `Offline since ${this.state.lastOnline}. Last ping ${dayjs().format("YYYY-MM-DD HH:mm:ss")}`
      //   }
      //   this.setState(newState)
      // })
      .catch(err => {
        const newState = {...this.state,
          status: `Offline since ${this.state.lastOnline}. Last attemped update ${dayjs().format("YYYY-MM-DD HH:mm:ss")}`
        }
        this.setState(newState)
      })
      await this.sleep(2000)
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
          
      </Layouts>
    )
  }

  private handleClick = () => {
    this.setState({ isOpen: !this.state.isOpen })
  }
}

export default RootContainer
