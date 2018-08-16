import { fromJS, Map } from "immutable"
import { IAction, LOADER } from "../actions"

const initialState = fromJS({
  data: Map,
  error: null,
  loading: false,
  success: false,
})

export default function AdminTabsReducer (state = initialState, action: IAction<string, {}>) {
  switch (action.type) {
    case LOADER.FAILURE:
    case LOADER.SUCCESS:
    case LOADER.GET_ALL_TABS:
      return state.merge(action.payload)
    default:
      return state
  }
}
