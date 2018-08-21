import { CONFIG, IAction } from "../actions"
import { defaultInitialState } from "../reducers"

export default function ConfigReducer (state = defaultInitialState, action: IAction<string, {}>) {
  switch (action.type) {
    case CONFIG.GET_ALL:
    case CONFIG.SUCCESS:
    case CONFIG.FAILURE:
      return state.merge(action.payload)
    default:
      return state
  }
}
