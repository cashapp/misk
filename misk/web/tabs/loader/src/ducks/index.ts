import { RouterState } from "connected-react-router"
import { combineReducers } from "redux"
import { all, fork } from "redux-saga/effects"
import { default as ItemReducer, IItemState, watchItemSagas } from "./item"
import { default as LoaderReducer, ILoaderState, watchLoaderSagas } from "./loader"
export * from "./item"
export * from "./loader"

/**
 * Redux Store State
 */
export interface IState {
  item: IItemState
  loader: ILoaderState
  router: RouterState
}

/**
 * Reducers
 */
export const rootReducer = combineReducers({
  item: ItemReducer,
  loader: LoaderReducer
})

/**
 * Sagas
 */
export function * rootSaga () {
  yield all([
    fork(watchItemSagas),
    fork(watchLoaderSagas)
  ])
}