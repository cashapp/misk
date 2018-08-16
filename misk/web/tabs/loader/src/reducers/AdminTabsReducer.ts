import { ADMINTABS, IAction } from "../actions"
import { initialState } from "../reducers"

export default function AdminTabsReducer (state = initialState, action: IAction<string, {}>) {
  switch (action.type) {
    case ADMINTABS.FAILURE:
    case ADMINTABS.SUCCESS:
    case ADMINTABS.GET_ADMINTABS:
      return state.merge(action.payload)
    default:
      return state
  }
}
