import { Intent } from "@blueprintjs/core"
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
import { HTTPMethod } from "http-method-enum"
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
 * Utils
 */
//TODO(adrw) upstream to @misk/core
export const HTTPMethodIntent: { [method in HTTPMethod]: Intent } = {
  [HTTPMethod.CONNECT]: Intent.DANGER,
  [HTTPMethod.DELETE]: Intent.DANGER,
  [HTTPMethod.GET]: Intent.PRIMARY,
  [HTTPMethod.HEAD]: Intent.WARNING,
  [HTTPMethod.OPTIONS]: Intent.NONE,
  [HTTPMethod.PATCH]: Intent.SUCCESS,
  [HTTPMethod.POST]: Intent.SUCCESS,
  [HTTPMethod.PUT]: Intent.SUCCESS,
  [HTTPMethod.TRACE]: Intent.NONE
}

export const HTTPStatusCodeIntent = (code: number) => {
  if (200 <= code && code < 300) {
    return Intent.SUCCESS
  } else if (300 <= code && code < 400) {
    return Intent.PRIMARY
  } else if (400 <= code && code < 500) {
    return Intent.WARNING
  } else if (500 <= code && code < 600) {
    return Intent.DANGER
  } else {
    return Intent.NONE
  }
}

export const HTTPMethodDispatch: any = (props: IDispatchProps) => ({
  [HTTPMethod.CONNECT]: props.simpleNetworkGet,
  [HTTPMethod.DELETE]: props.simpleNetworkDelete,
  [HTTPMethod.GET]: props.simpleNetworkGet,
  [HTTPMethod.HEAD]: props.simpleNetworkHead,
  [HTTPMethod.OPTIONS]: props.simpleNetworkGet,
  [HTTPMethod.PATCH]: props.simpleNetworkPatch,
  [HTTPMethod.POST]: props.simpleNetworkPost,
  [HTTPMethod.PUT]: props.simpleNetworkPut,
  [HTTPMethod.TRACE]: props.simpleNetworkGet
})

/**
 * Redux Store State
 */
export interface IState {
  webActions: IWebActionsState
  router: Reducer<RouterState, LocationChangeAction>
  simpleForm: ISimpleFormState
  simpleNetwork: ISimpleNetworkState
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
  webActions: simpleRootSelector<IState, IWebActionsImmutableState>(
    "webActions",
    state
  ),
  simpleForm: simpleRootSelector<IState, ISimpleFormImmutableState>(
    "simpleForm",
    state
  ),
  simpleNetwork: simpleRootSelector<IState, ISimpleNetworkImmutableState>(
    "simpleNetwork",
    state
  )
})

/**
 * Reducers
 */
export const rootReducer = (history: History): Reducer<any, AnyAction> =>
  combineReducers({
    webActions: WebActionsReducer,
    router: connectRouter(history),
    simpleForm: SimpleFormReducer,
    simpleNetwork: SimpleNetworkReducer
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
