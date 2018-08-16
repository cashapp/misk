import { IAction, ITEM } from "../actions"
import { initialState } from "../reducers"

export default function ItemReducer (state = initialState, action: IAction<string, {}>) {
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
