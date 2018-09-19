import { RouterState } from "connected-react-router"
import { combineReducers } from "redux"
import { all, fork } from "redux-saga/effects"
import { default as ExampleReducer, IExampleState, watchExampleSagas } from "./example"
export * from "./example"

/**
 * Redux Store State
 */
export interface IState {
  example: IExampleState
  router: RouterState
}

/**
 * Reducers
 */
export const rootReducer = combineReducers({
  example: ExampleReducer
})

/**
 * Sagas
 */
export function * rootSaga () {
  yield all([
    fork(watchExampleSagas)
  ])
}
