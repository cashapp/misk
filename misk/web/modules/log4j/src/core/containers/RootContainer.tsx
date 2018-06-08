import React from 'react';
import { observe, streamProps } from 'frint-react';

import {
  changeColor
} from '../actions';
import { COLORS } from '../constants';

export namespace RootContainer {
  export interface Props {
    color: string,
    backgroundColor: string,
    changeColor: any,
    counter: number
  }
}

class RootContainer extends React.Component<RootContainer.Props> {
  render() {
    const codeStyle = {
      color: this.props.color,
      backgroundColor: this.props.color
    };

    return (
      <div>
        <h5>App: Log4j</h5>

        <p>Color value in <strong>Log4j</strong>: <code style={codeStyle}>{this.props.color}</code></p>

        <div>
          <button
            className="button"
            style={{backgroundColor: COLORS.GREEN, color: '#fff'}}
            onClick={() => this.props.changeColor(COLORS.GREEN)}
          >
            Green
          </button>

          <button
            className="button"
            style={{backgroundColor: COLORS.RED, color: '#fff'}}
            onClick={() => this.props.changeColor(COLORS.RED)}
          >
            Red
          </button>
        </div>

        <p>Counter value from <strong>Threads</strong>: <code>{this.props.counter}</code></p>
      </div>
    );
  }
}

export default observe(function (app) { // eslint-disable-line func-names
  const store : any = app.get('store');

  return streamProps()
    .setDispatch(
      { changeColor },
      store
    )

    .set(
      store.getState$(),
      (state: any) => ({ color: state.color.value })
    )

    .set(
      app.getAppOnceAvailable$('ThreadsApp'),
      (threadsApp: any) => threadsApp.get('store').getState$(),
      (threadsState: any) => ({ counter: threadsState.counter.value })
    )

    .get$();
})(RootContainer);
