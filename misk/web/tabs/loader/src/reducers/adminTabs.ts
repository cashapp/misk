import { fromJS, List } from "immutable"
import { IAction, LOADER } from "../actions"

const initialState = fromJS({
  data: List([]),
  error: null,
  loading: false,
  success: false,
})

export default function adminTabsReducer (state = initialState, action: IAction<string, {}>) {
  switch (action.type) {
    case LOADER.FAILURE:
    case LOADER.SUCCESS:
    case LOADER.GET_ADMINTABS:
      return state.merge(action.payload)
    default:
      return state
  }
}
