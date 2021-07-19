import {
  dispatchSimpleRedux,
  IDispatchSimpleRedux,
  ISimpleReduxImmutableState,
  ISimpleReduxState,
  SimpleReduxReducer,
  SimpleReduxSaga,
  simpleRootSelector,
  watchSimpleReduxSagas
} from "@misk/simpleredux"
import {
  connectRouter,
  LocationChangeAction,
  RouterState
} from "connected-react-router"
import { History } from "history"
import { AnyAction, combineReducers, Reducer } from "redux"
import { all, fork } from "redux-saga/effects"

/**
 * Redux Store State
 */
export interface IState {
  router: Reducer<RouterState, LocationChangeAction>
  simpleRedux: ISimpleReduxState
}

/**
 * Dispatcher
 */
export interface IDispatchProps extends IDispatchSimpleRedux {}

export const rootDispatcher: IDispatchProps = {
  ...dispatchSimpleRedux
}

/**
 * State Selectors
 */
export const rootSelectors = (state: IState) => ({
  simpleRedux: simpleRootSelector<IState, ISimpleReduxImmutableState>(
    "simpleRedux",
    state
  )
})

/**
 * Reducers
 */
export const rootReducer = (history: History): Reducer<any, AnyAction> =>
  combineReducers({
    router: connectRouter(history),
    simpleRedux: SimpleReduxReducer
  })

/**
 * Sagas
 */
export function* rootSaga(): SimpleReduxSaga {
  yield all([fork(watchSimpleReduxSagas)])
}
