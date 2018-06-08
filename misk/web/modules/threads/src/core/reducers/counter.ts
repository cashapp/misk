import { Action } from '../actions';
import { COUNTER } from '../constants'

const INITIAL_STATE = {
  value: 5
};

export default function counter(state = INITIAL_STATE, action: Action) {
  switch (action.type) {
    case COUNTER.INCREMENT:
      return Object.assign({}, {
        value: state.value + 1
      });

    case COUNTER.DECREMENT:
      return Object.assign({}, {
        value: state.value - 1
      });

    default:
      return state;
  }
}
