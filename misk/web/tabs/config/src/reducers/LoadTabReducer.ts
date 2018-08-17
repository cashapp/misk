import { fromJS, Map } from "immutable"
import { IAction, LOADER } from "../actions"

const initialState = fromJS({
  data: Map(),
  error: null,
  loading: false,
  success: false,
})

export default function loadTabReducer (state = initialState, action: IAction<string, {}>) {
  switch (action.type) {
    case LOADER.GET_ONE_COMPONENT:
    case LOADER.GET_ALL_COMPONENTS_AND_TABS:
    case LOADER.GET_ALL_TABS:
    case LOADER.SUCCESS:
    case LOADER.FAILURE:
      return state.mergeDeep(action.payload)
    default:
      return state
  }
}
