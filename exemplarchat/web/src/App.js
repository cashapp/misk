import React, { Component } from 'react';
import logo from './logo.svg';
import './App.css';

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
    const room = href.substr(href.lastIndexOf('/') + 1);
    const ws_url = `ws://localhost:8080/room/${room}`;
    var ws = new WebSocket(ws_url);
    ws.onmessage = this.handleWebSocketMessage.bind(this);
    this.setState({ room: room, ws: ws });
  }

  handlePendingMessageChange(event) {
    this.setState({pendingMessage: event.target.value});
  }

  handleWebSocketMessage(message) {
    this.setState((prevState, props) => {
      return { messages: prevState.messages.concat(message.data) };
    });
  }

  sendMessage(e) {
    if (e.charCode == 13) {
      this.state.ws.send(this.state.pendingMessage);
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
