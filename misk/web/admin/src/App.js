import React, { Component } from 'react';
import './App.css';
import Health from './Health.js';

class App extends Component {
  // TODO: route instead of blindly returning Health
  render() {
    return (
      <Health />
    );
  }
}

export default App;
