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
    isOpen: false
  };

  private handleClick = () => {
    this.setState({ isOpen: !this.state.isOpen });
  };

  componentDidMount() {
    axios
      .get("http://0.0.0.0:8080/_admin/config/all")
      .then(response => {
        // const newState = Object.assign({}, this.state, {
        //   config: { response }
        // })
      console.log(response)
      // console.log(this.state.config)
      // this.setState(newState)
      });
    // console.log(this.state.config)
  }

  render() {
      axios
        .get("/_admin/config/all")
        .then(response => {
          // const newState = Object.assign({}, this.state, {
          //   config: { response }
          // })
          console.log(response)
          // console.log(this.state.config)
          // this.setState(newState)
        });
      // console.log(this.state.config)

    return (
      <div>
        <h5>App: Config</h5>
        <p>anotherconfigtest123456789</p>

        <h5>Application Config</h5>
        <Button onClick={this.handleClick}>
          {this.state.isOpen ? "Hide" : "Show"} build logs
        </Button>
        <Collapse isOpen={this.state.isOpen}>
          <pre><code>
            _p2_restart: Restarted by mgersh at Wed, 16 May 2018 22:46:29 GMT
            app:
            badgingEnabled: true
            pushEnabled: true
            appVerifier:
            enableConfirmationViaPaymentToken: true
            paymentTokenUrlPrefix:
            app_session_token_hmac_key: !secret ['/data/pods/franklin/secrets/app_session_token_hmac_key']
            backfiller:
          </code></pre>
        </Collapse>

        <h5>Resolved Datasource JDBC URLs</h5>
        <h5>P2 Datasource Config</h5>
        <h5>P2 Config (overlayed into App Config)</h5>
        <h5>Environment</h5>
      </div>
    );
  }
}

export default RootContainer;
