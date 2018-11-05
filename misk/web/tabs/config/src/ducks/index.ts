import {
  connectRouter,
  LocationChangeAction,
  RouterState
} from "connected-react-router"
import { History } from "history"
import { combineReducers, Reducer } from "redux"
import { all, fork } from "redux-saga/effects"
import {
  default as ConfigReducer,
  IConfigState,
  watchConfigSagas
} from "./config"
export * from "./config"

/**
 * Redux Store State
 */
export interface IState {
  config: IConfigState
  router: Reducer<RouterState, LocationChangeAction>
}

/**
 * Reducers
 */
export const rootReducer = (history: History) =>
  combineReducers({
    config: ConfigReducer,
    router: connectRouter(history)
  })

/**
 * Sagas
 */
export function* rootSaga() {
  yield all([fork(watchConfigSagas)])
}
