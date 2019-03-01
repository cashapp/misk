import {
  createAction,
  IAction,
  IRootState,
  defaultRootState
} from "@misk/simpleredux"
import axios from "axios"
import { all, AllEffect, call, put, takeLatest } from "redux-saga/effects"

/**
 * Actions
 * string enum of the defined actions that is used as type enforcement for Reducer and Sagas arguments
 */
export enum WEBACTIONS {
  DINOSAUR = "WEBACTIONS_DINOSAUR",
  SUCCESS = "WEBACTIONS_SUCCESS",
  FAILURE = "WEBACTIONS_FAILURE"
}

/**
 * Dispatch Object
 * Object of functions that dispatch Actions with standard defaults and any required passed in input
 * dispatch Object is used within containers to initiate any saga provided functionality
 */
export interface IWebActionsPayload {
  data?: any
  error: any
  loading: boolean
  success: boolean
}

export interface IDispatchWebActions {
  webActionsDinosaur: (
    data: any,
    fieldTag: string,
    formTag: string
  ) => IAction<WEBACTIONS.DINOSAUR, IWebActionsPayload>
  webActionsFailure: (
    error: any
  ) => IAction<WEBACTIONS.FAILURE, IWebActionsPayload>
  webActionsSuccess: (
    data: any
  ) => IAction<WEBACTIONS.SUCCESS, IWebActionsPayload>
}

export const dispatchWebActions: IDispatchWebActions = {
  webActionsDinosaur: () =>
    createAction<WEBACTIONS.DINOSAUR, IWebActionsPayload>(WEBACTIONS.DINOSAUR, {
      error: null,
      loading: true,
      success: false
    }),
  webActionsFailure: (error: any) =>
    createAction<WEBACTIONS.FAILURE, IWebActionsPayload>(WEBACTIONS.FAILURE, {
      ...error,
      loading: false,
      success: false
    }),
  webActionsSuccess: (data: any) =>
    createAction<WEBACTIONS.SUCCESS, IWebActionsPayload>(WEBACTIONS.SUCCESS, {
      ...data,
      error: null,
      loading: false,
      success: true
    })
}

/**
 * Sagas are generating functions that consume actions and
 * pass either latest (takeLatest) or every (takeEvery) action
 * to a handling generating function.
 *
 * Handling function is where obtaining web resources is done
 * Web requests are done within try/catch so that
 *  if request fails: a failure action is dispatched
 *  if request succeeds: a success action with the data is dispatched
 * Further processing of the data should be minimized within the handling
 *  function to prevent unhelpful errors. Ie. a failed request error is
 *  returned but it actually was just a parsing error within the try/catch.
 */
function* handleDinosaur(action: IAction<WEBACTIONS, IWebActionsPayload>) {
  try {
    const { data } = yield call(
      axios.get,
      "https://jsonplaceholder.typicode.com/posts/"
    )
    yield put(dispatchWebActions.webActionsSuccess({ data }))
  } catch (e) {
    yield put(dispatchWebActions.webActionsFailure({ error: { ...e } }))
  }
}

export function* watchWebActionsSagas(): IterableIterator<AllEffect> {
  yield all([takeLatest(WEBACTIONS.DINOSAUR, handleDinosaur)])
}

/**
 * Initial State
 * Reducer merges all changes from dispatched action objects on to this initial state
 */
const initialState = defaultRootState("webActions")

/**
 * Duck Reducer
 * Merges dispatched action objects on to the existing (or initial) state to generate new state
 */
export const WebActionsReducer = (
  state = initialState,
  action: IAction<WEBACTIONS, {}>
) => {
  switch (action.type) {
    case WEBACTIONS.DINOSAUR:
    case WEBACTIONS.FAILURE:
    case WEBACTIONS.SUCCESS:
      return state.merge(action.payload)
    default:
      return state
  }
}

/**
 * State Interface
 * Provides a complete Typescript interface for the object on state that this duck manages
 * Consumed by the root reducer in ./ducks index to update global state
 * Duck state is attached at the root level of global state
 */
export interface IWebActionsState extends IRootState {
  [key: string]: any
}

export interface IWebActionsImmutableState {
  toJS: () => IWebActionsState
}
