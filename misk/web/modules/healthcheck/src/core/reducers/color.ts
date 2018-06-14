import { Action } from '../actions';
import { COLOR, COLORS } from '../constants';

const INITIAL_STATE = {
  value: COLORS.DEFAULT
};

export default function color(state = INITIAL_STATE, action: Action) {
  switch (action.type) {
    case COLOR.CHANGE:
      return Object.assign({}, {
        value: action.color
      });

    default:
      return state;
  }
}
