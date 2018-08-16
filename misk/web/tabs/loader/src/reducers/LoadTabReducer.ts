import { fromJS, Map } from "immutable"
import { IAction, LOADTAB } from "../actions"

const initialState = fromJS({
  data: Map(),
  error: null,
  loading: false,
  success: false,
})

export default function loadTabReducer (state = initialState, action: IAction<string, {}>) {
  switch (action.type) {
    case LOADTAB.GET_ONE:
    case LOADTAB.SUCCESS:
    case LOADTAB.FAILURE:
      return state.mergeDeep(action.payload)
    default:
      return state
  }
}
