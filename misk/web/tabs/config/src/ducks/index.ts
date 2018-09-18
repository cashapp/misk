import { RouterState } from "connected-react-router"
import { combineReducers } from "redux"
import { all, fork } from "redux-saga/effects"
import { default as ConfigReducer, IConfigState, watchConfigSagas } from "./config"
export * from "./config"

/**
 * Redux Store State
 */
export interface IState {
  config: IConfigState
  router: RouterState
}

/**
 * Reducers
 */
export const rootReducer = combineReducers({
  config: ConfigReducer
})

/**
 * Sagas
 */
export function * rootSaga () {
  yield all([
    fork(watchConfigSagas)
  ])
}
