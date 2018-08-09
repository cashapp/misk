import { combineReducers } from "redux"
import counterReducer from "./counter"

const rootReducer = combineReducers({
  count: counterReducer,
})

export interface IReducerState {
  count: number
}

export default rootReducer