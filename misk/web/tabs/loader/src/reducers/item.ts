import { fromJS, List } from "immutable"
import { IAction, ITEM } from "../actions"

const initialState = fromJS({
  data: List([]),
  error: null,
  loading: false,
  success: false,
})

export default function itemReducer (state = initialState, action: IAction<string, {}>) {
  switch (action.type) {
    case ITEM.GET:
    case ITEM.GET_ONE:
    case ITEM.SAVE:
    case ITEM.UPDATE:
    case ITEM.DELETE:
    case ITEM.SUCCESS:
    case ITEM.FAILURE:
      return state.merge(action.payload)
    default:
      return state
  }
}
