import React from 'react';
import { Button, Collapse } from '@blueprintjs/core';
import axios from 'axios';

export namespace RootContainer {
  export interface Props {
  }
}

// potentially have bool array for the collapse-ibles and accept array of text as props

class RootContainer extends React.Component<RootContainer.Props> {
  public state = {
    isOpen: false,
    config: {
      data: {
        effective_config: ""
      }
    }
  };

  private handleClick = () => {
    this.setState({ isOpen: !this.state.isOpen });
  };

  componentDidMount() {
    axios
      .get("/_admin/config/all")
      .then(response => {
        const data = response.data
        const newState = Object.assign({}, this.state, {
            config: { data }
        })
        console.log(response)
        this.setState(newState)
        // console.log(this.state)
      });
  }

  render() {
    return (
      <div>
        <h5>App: Config</h5>

        <h5>Effective Config</h5>
        <Button onClick={this.handleClick}>
          {this.state.isOpen ? "Hide" : "Show"} build logs
        </Button>
        <Collapse isOpen={this.state.isOpen}>
          <pre><code>
              {JSON.stringify(this.state.config.data, null, 2)}
          </code></pre>
        </Collapse>

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
      </div>
    );
  }
}

export default RootContainer;
