import {
  IRouterProvidedProps,
  SimpleReduxSaga,
  simpleRootSelector,
  ISimpleReduxState,
  dispatchSimpleRedux,
  IDispatchSimpleRedux,
  ISimpleReduxImmutableState,
  SimpleReduxReducer,
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
export interface IDispatchProps
  extends IDispatchSimpleRedux,
    IRouterProvidedProps {}

export const rootDispatcher: IDispatchProps = {
  ...dispatchSimpleRedux
}

/**
 * State Selectors
 */
export const rootSelectors = (state: IState) => ({
  router: state.router,
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

/**
 * Map Dispatch/State to Props
 */
export const mapStateToProps = (state: IState) => rootSelectors(state)

export const mapDispatchToProps: IDispatchProps = {
  ...rootDispatcher
}
