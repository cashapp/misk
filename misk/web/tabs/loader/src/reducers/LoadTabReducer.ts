import { IAction, LOADTAB } from "../actions"
import { initialState } from "../reducers"

export default function loadTabReducer (state = initialState, action: IAction<string, {}>) {
  switch (action.type) {
    case LOADTAB.GET_ONE:
    case LOADTAB.SUCCESS:
    case LOADTAB.FAILURE:
      return state.merge(action.payload)
    default:
      return state
  }
}
