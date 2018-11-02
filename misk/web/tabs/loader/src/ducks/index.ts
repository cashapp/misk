import { RouterState } from "connected-react-router"
import { combineReducers } from "redux"
import { all, fork } from "redux-saga/effects"
import {
  default as LoaderReducer,
  ILoaderState,
  watchLoaderSagas
} from "./loader"
export * from "./loader"

/**
 * Redux Store State
 */
export interface IState {
  loader: ILoaderState
  router: RouterState
}

/**
 * Reducers
 */
export const rootReducer = combineReducers({
  loader: LoaderReducer
})

/**
 * Sagas
 */
export function* rootSaga() {
  yield all([fork(watchLoaderSagas)])
}
