import { fromJS, List } from "immutable"
import { ADMINTABS, IAction } from "../actions"

const initialState = fromJS({
  data: List([]),
  error: null,
  loading: false,
  success: false,
})

export default function adminTabsReducer (state = initialState, action: IAction<string, {}>) {
  switch (action.type) {
    case ADMINTABS.FAILURE:
    case ADMINTABS.SUCCESS:
    case ADMINTABS.GET_ADMINTABS:
      return state.merge(action.payload)
    default:
      return state
  }
}
