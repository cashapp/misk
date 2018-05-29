import React, { Component } from 'react';
import './App.css';
import ReconnectingWebSocket from 'reconnecting-websocket'

class App extends Component {
  constructor() {
    super();

    this.state = {
      pendingMessage: "",
      messages: [],
    };
  }

  componentDidMount() {
    const href = window.location.href;
    const ws_protocol = (window.location.protocol === "https:") ? "wss:" : "ws:";
    const hostname = window.location.hostname;
    const port = window.location.port;
    const room = href.substr(href.lastIndexOf('/') + 1);
    const ws_url = `${ws_protocol}//${hostname}:${port}/room/${room}`

    this.ws = new ReconnectingWebSocket(ws_url);
    this.ws.onmessage = this.handleWebSocketMessage.bind(this);
    this.ws.onopen = this.handleWebSocketOpen.bind(this);
    this.ws.onclose = this.handleWebSocketClose.bind(this);
    this.setState({ room: room });
  }

  handlePendingMessageChange(event) {
    this.setState({pendingMessage: event.target.value});
  }

  handleWebSocketMessage(message) {
    this.setState((prevState, props) => {
      return { messages: prevState.messages.concat(message.data) };
    });
  }

  handleWebSocketOpen(event) {
    console.log('Opened!');
  }

  handleWebSocketClose(event) {
    console.log('Closed!');
  }

  sendMessage(e) {
    if (e.charCode === 13) {
      this.ws.send(this.state.pendingMessage);
      this.setState({ pendingMessage: "" });
      e.preventDefault();
    }
  }

  render() {
    return (
      <div className="App">
        <h1 className="App-title">Chat Room: {this.state.room}</h1>
        <ul>
          {this.state.messages.map(function(listValue, i){
            return <li key={i}><b>Unknown User: </b>{listValue}</li>;
          })}
        </ul>

        <textarea
          className="Message-input"
          value={this.state.pendingMessage}
          onChange={(e) => this.handlePendingMessageChange(e) }
          onKeyPress={(e) => this.sendMessage(e) }
        />
      </div>
    );
  }
}

export default App;
