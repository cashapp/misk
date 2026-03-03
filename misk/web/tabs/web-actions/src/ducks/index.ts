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
import {
  dispatchWebActions,
  IDispatchWebActions,
  IWebActionsImmutableState,
  IWebActionsState,
  watchWebActionsSagas,
  WebActionsReducer
} from "./webActions"
export * from "./webActions"

/**
 * Redux Store State
 */
export interface IState {
  router: Reducer<RouterState, LocationChangeAction>
  simpleRedux: ISimpleReduxState
  webActions: IWebActionsState
  webActionsRaw: IWebActionsImmutableState
}

/**
 * Dispatcher
 */
export interface IDispatchProps
  extends IDispatchSimpleRedux,
    IDispatchWebActions {}

export const rootDispatcher: IDispatchProps = {
  ...dispatchSimpleRedux,
  ...dispatchWebActions
}

/**
 * State Selectors
 */
export const rootSelectors = (state: IState) => ({
  router: state.router,
  simpleRedux: simpleRootSelector<IState, ISimpleReduxImmutableState>(
    "simpleRedux",
    state
  ),
  webActions: simpleRootSelector<IState, IWebActionsImmutableState>(
    "webActions",
    state
  ),
  webActionsRaw: state.webActionsRaw
})

/**
 * Reducers
 */
export const rootReducer = (history: History): Reducer<any, AnyAction> =>
  combineReducers({
    router: connectRouter(history),
    simpleRedux: SimpleReduxReducer,
    webActions: WebActionsReducer,
    webActionsRaw: WebActionsReducer
  })

/**
 * Sagas
 */
export function* rootSaga(): SimpleReduxSaga {
  yield all([fork(watchWebActionsSagas), fork(watchSimpleReduxSagas)])
}

/**
 * Map Dispatch/State to Props
 */
export const mapStateToProps = (state: IState) => rootSelectors(state)

export const mapDispatchToProps: IDispatchProps = {
  ...rootDispatcher
}
