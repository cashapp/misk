import { RouterState } from "connected-react-router"
import { combineReducers } from "redux"
// import counterReducer from './counter'

const rootReducer = combineReducers({
  // count: counterReducer,
})

export interface State {
  count: number
  router: RouterState
}

export default rootReducer