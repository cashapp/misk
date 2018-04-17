import React, { Component } from 'react';
import './App.css';
import ReconnectingWebSocket from 'reconnecting-websocket'

class Health extends Component {
  constructor() {
    super();
    this.state = {
      clusterHealth: {}
    };
  }

  componentDidMount() {
    const ws_protocol = (window.location.protocol === "https:") ? "wss:" : "ws:";
    const hostname = window.location.hostname;
    const port = window.location.port;
    const ws_url = `${ws_protocol}//${hostname}:${port}/health`

    this.ws = new ReconnectingWebSocket(ws_url);
    this.ws.onmessage = this.handleWebSocketMessage.bind(this);
  }

  handleWebSocketMessage(message) {
    const clusterHealth = JSON.parse(message.data);
    this.setState({clusterHealth: clusterHealth});
  }

  render() {
    return (
      <div>
        <h1>Cluster Health</h1>
        <table>
          <thead>
            <tr>
              <th>Host</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {
              Object.keys(this.state.clusterHealth).map(key => {
                return (
                  <tr key={key}>
                    <td>{key}</td>
                    <td>
                      {
                        this.state.clusterHealth[key].healthStatuses.map(item => {
                          return (
                            <p>{item.name} ({item.isHealthy ? "Healthy" : "Unhealthy"})</p>
                          )
                        })
                      }
                     </td>
                  </tr>
                )
              })
            }
          </tbody>
        </table>
      </div>
    );
  }
}

export default Health;
