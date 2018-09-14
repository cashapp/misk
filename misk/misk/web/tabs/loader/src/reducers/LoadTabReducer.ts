import { fromJS, Map } from "immutable"
import { IMiskAction, LOADER } from "../actions"

const initialState = fromJS({
  data: Map(),
  error: null,
  loading: false,
  success: false,
})

export default function loadTabReducer (state = initialState, action: IMiskAction<string, {}>) {
  switch (action.type) {
    case LOADER.FAILURE:
    case LOADER.GET_ONE_COMPONENT:
    case LOADER.GET_ALL_TABS:
    case LOADER.SUCCESS:
      return state.mergeDeep(action.payload)
    default:
      return state
  }
}
