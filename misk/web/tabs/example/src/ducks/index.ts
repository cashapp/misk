import {
  connectRouter,
  LocationChangeAction,
  RouterState
} from "connected-react-router"
import { History } from "history"
import { combineReducers, Reducer } from "redux"
import { all, fork } from "redux-saga/effects"
import {
  default as ExampleReducer,
  IExampleState,
  watchExampleSagas
} from "./example"
export * from "./example"

/**
 * Redux Store State
 */
export interface IState {
  example: IExampleState
  router: Reducer<RouterState, LocationChangeAction>
}

/**
 * Reducers
 */
export const rootReducer = (history: History) =>
  combineReducers({
    example: ExampleReducer,
    router: connectRouter(history)
  })

/**
 * Sagas
 */
export function* rootSaga() {
  yield all([fork(watchExampleSagas)])
}
