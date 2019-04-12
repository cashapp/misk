import {
  dispatchSimpleForm,
  dispatchSimpleNetwork,
  IDispatchSimpleForm,
  IDispatchSimpleNetwork,
  ISimpleFormImmutableState,
  ISimpleFormState,
  ISimpleNetworkImmutableState,
  ISimpleNetworkState,
  SimpleFormReducer,
  SimpleNetworkReducer,
  simpleRootSelector,
  watchSimpleFormSagas,
  watchSimpleNetworkSagas
} from "@misk/simpleredux"
import {
  connectRouter,
  LocationChangeAction,
  RouterState
} from "connected-react-router"
import { History } from "history"
import { AnyAction, combineReducers, Reducer } from "redux"
import { all, AllEffect, fork } from "redux-saga/effects"
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
  simpleForm: ISimpleFormState
  simpleNetwork: ISimpleNetworkState
  webActions: IWebActionsState
  webActionsRaw: IWebActionsImmutableState
}

/**
 * Dispatcher
 */
export interface IDispatchProps
  extends IDispatchSimpleForm,
    IDispatchSimpleNetwork,
    IDispatchWebActions {}

export const rootDispatcher: IDispatchProps = {
  ...dispatchSimpleForm,
  ...dispatchSimpleNetwork,
  ...dispatchWebActions
}

/**
 * State Selectors
 */
export const rootSelectors = (state: IState) => ({
  simpleForm: simpleRootSelector<IState, ISimpleFormImmutableState>(
    "simpleForm",
    state
  ),
  simpleNetwork: simpleRootSelector<IState, ISimpleNetworkImmutableState>(
    "simpleNetwork",
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
    simpleForm: SimpleFormReducer,
    simpleNetwork: SimpleNetworkReducer,
    webActions: WebActionsReducer,
    webActionsRaw: WebActionsReducer
  })

/**
 * Sagas
 */
export function* rootSaga(): IterableIterator<AllEffect> {
  yield all([
    fork(watchWebActionsSagas),
    fork(watchSimpleFormSagas),
    fork(watchSimpleNetworkSagas)
  ])
}
