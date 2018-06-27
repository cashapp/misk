import { Button, Collapse } from "@blueprintjs/core"
import axios from "axios"
import React from "react"
import Layouts from "../../layouts"

// potentially have bool array for the collapse-ibles and accept array of text as props

class RootContainer extends React.Component {
  public state = {
    config: {
      data: {
        effective_config: ""
      }
    },
    isOpen: false
  }

  componentDidMount() {
    axios
      .get("http://localhost:8080/api/config/all")
      .then(response => {
        const data = response.data
        const newState = {...this.state, 
          config: { data }}
        console.log(response)
        this.setState(newState)
        // console.log(this.state)
      })
  }

  render() {
    return (
      <Layouts>
        <h5>App: Healthcheck</h5>

        

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
