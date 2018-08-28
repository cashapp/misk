import { RouterState } from "connected-react-router"
import { fromJS, List } from "immutable"
import { combineReducers } from "redux"
import ConfigReducer from "./ConfigReducer"

export const defaultInitialState = fromJS({
  data: List([]),
  error: null,
  loading: false,
  success: false,
})

const rootReducer = combineReducers({
  config: ConfigReducer,
})

export interface IState {
  config: any
  router: RouterState
}

export default rootReducer
