import React from 'react';
import { observe, streamProps } from 'frint-react';

import {
  incrementCounter,
  decrementCounter,
} from '../actions';

export namespace Root {
  export interface Props {
    color: string, 
    backgroundColor: string, 
    counter: number, 
    incrementCounter: any, 
    decrementCounter: any
  }
}

class Root extends React.Component<Root.Props> {
  render() {
    const codeStyle = {
      color: this.props.color,
      backgroundColor: this.props.color
    };

    return (
      <div>
        <h5>App: Threads</h5>

        <p>Counter value in <strong>Threads</strong>: <code>{this.props.counter}</code></p>

        <div>
          <button
            className="button button-primary"
            onClick={() => this.props.incrementCounter()}
          >
            +
          </button>

          <button
            className="button"
            onClick={() => this.props.decrementCounter()}
          >
            -
          </button>
        </div>

        <p>Color value from <strong>Log4j</strong>: <code style={codeStyle}>{this.props.color}</code></p>
      </div>
    );
  }
}

export default observe(function (app) { // eslint-disable-line func-names
  const store : any = app.get('store');

  return streamProps()
    .setDispatch({
      incrementCounter,
      decrementCounter,
    }, store)

    .set(
      store.getState$(),
      (state: any) => ({ counter: state.counter.value })
    )

    .set(
      app.getAppOnceAvailable$('Log4jApp'),
      (log4jApp: any) => log4jApp.get('store').getState$(),
      (log4jState: any) => ({ color: log4jState.color.value })
    )

    .get$();
})(Root);
