import {
  createAction,
  defaultState,
  IAction,
  IDefaultState
} from "@misk/common"
import axios from "axios"
import { fromJS } from "immutable"
import { all, call, put, takeLatest } from "redux-saga/effects"

/**
 * This is a Ducks module
 *
 * A Ducks module contains many different parts that work together to provide a complete
 * processing unit for a Redux-Sagas state domain.
 *
 * First, some terminology:
 * State: A central store of the React app's knowledge at a given point in time
 * Redux: unidirectional data flow state management for React (in the spirit of Flux)
 * Redux-Sagas: a Redux pattern that uses Actions, Dispatchers, Sagas, and Reducers to handle state updates
 * Actions: small context objects used to pass around and process state changes. In Misk, they will by default
 *      have fields for `data`, `error`, `loading`, and `success`.
 * Dispatchers: An object that has functions that dispatch actions. The Dispatcher has one function for each action.
 *      The advantages of a Dispatcher and dispatching functions are as follows:
 *        - Import a single Dispatcher object and have access to all action-dispatching functions
 *        - Dispatching function ensures consistent formatting for emitted actions
 *        - Dispatching function can handle any marshalling or default values to set in the action
 * Sagas: A generating function that handles actions asynchronously. Each Duck Saga is registered with the tab
 *       rootSaga and has an array of take{Latest|Every} functions that bind an action to a handling function.
 *       The handling function is also an asynchronous generating function that does any web requests or blocking
 *       state change computation. In the process of computation, it can yield other actions to signal
 *       success, failure, or progress which will consequently be picked up and handled asynchronously by other handlers.
 * Reducers: Maintain up to date state by continuing to merge in changes as they come from dispatched actions.
 *       Each Duck Reducer is registered with the tab's rootReducer to provide a single merged reducer for all state change.
 *       Each Duck Reducer effectively is responsible for a domain or keyspace of the global state object.
 *       The structure and typing of this state domain is defined in the Duck State Interface.
 * Interface: a Typescript specific syntax that allows definition of an object interface with expected keys and value types
 * Ducks: a Redux-Sagas pattern that puts all elements in a single Ducks module file instead of different directories
 *
 * Why would you need a Ducks module
 * By convention in Misk, state is not ever updated directly from a React container or component.
 * This idiom is called unidirectional data flow and it makes for easier state management in React apps.
 * The current state is always displayed by the React container/component but any changes are handled by
 * a Ducks module. This separates the View and Controller functionality of a React app.
 *
 * Instead it is updated through a Ducks module. This ensures predictable and debuggable state updates
 * because a single rootSaga and rootReducer handle all state change Actions across the entire Misk tab.
 * The Ducks pattern also makes use of modern Javascript generating functions which allow for asynchronous
 * processing of all non-View related computation letting you build non-View-blocking React apps.
 *
 * Pro tip: Use the Redux DevTools Chrome plugin to see all state changes in your tab as they occur.
 * https://chrome.google.com/webstore/detail/redux-devtools/lmhkpmbekcpmknklioeibfkpmmfibljd
 *
 * Using the Redux-Sagas Ducks pattern helps developers easily build Misk tabs with minimal
 * front end React, Redux, or Sagas knowledge.
 *
 * Getting Started
 * If your tab ever retrieves resources from the Web or from Misk server, you need a Ducks module.
 * Your Ducks module will need the required elements (Actions, Dispatchers, Sagas, Reducers, State Interface)
 * Specifically it will need to export:
 *  - Dispatcher object: contains all of your dispatcher functions that trigger Actions
 *  - watchSagas function: which is your Ducks Saga for handing off Actions to the correct handling function
 *  - Reducer: to handle merging in state changes to the domain of the state your Ducks manages
 *  - State Interface: Typescript interface for what your Ducks state contains
 *
 * These must all be imported into src/ducks/index.ts and wired up to respective rootReducer, rootSaga,
 * and global State Interfaces.
 *
 * Displaying State
 * In a Container, you can wire up your Dispatcher object and Ducks State to be props accessible in the container.
 * This is done using mapStateToProps and mapDispatchToProps functions.
 * Your props will then always have the up to date state which you can render with regular React render(),
 * and it will always have a mounted function from your Dispatcher object so you can trigger a given action
 * to kick off a Ducks flow to retrieve data or do other asynchronous computation.
 */

/**
 * Actions
 * string enum of the defined actions that is used as type enforcement for Reducer and Sagas arguments
 */
export enum EXAMPLE {
  GET = "EXAMPLE_GET",
  GET_ONE = "EXAMPLE_GET_ONE",
  SAVE = "EXAMPLE_SAVE",
  PUT = "EXAMPLE_PUT",
  PATCH = "EXAMPLE_PATCH",
  DELETE = "EXAMPLE_DELETE",
  SUCCESS = "EXAMPLE_SUCCESS",
  FAILURE = "EXAMPLE_FAILURE"
}

/**
 * Dispatch Object
 * Object of functions that dispatch Actions with standard defaults and any required passed in input
 * dispatch Object is used within containers to initiate any saga provided functionality
 */
export const dispatchExample = {
  delete: (id: number) =>
    createAction(EXAMPLE.DELETE, {
      id,
      loading: true,
      success: false,
      error: null
    }),
  failure: (error: any) =>
    createAction(EXAMPLE.FAILURE, { ...error, loading: false, success: false }),
  patch: (id: number, data: any) =>
    createAction(EXAMPLE.PATCH, {
      id,
      ...data,
      loading: true,
      success: false,
      error: null
    }),
  put: (id: number, data: any) =>
    createAction(EXAMPLE.PUT, {
      id,
      ...data,
      loading: true,
      success: false,
      error: null
    }),
  request: () =>
    createAction(EXAMPLE.GET, { loading: true, success: false, error: null }),
  requestOne: (id: number) =>
    createAction(EXAMPLE.GET_ONE, {
      id,
      loading: true,
      success: false,
      error: null
    }),
  save: (data: any) =>
    createAction(EXAMPLE.SAVE, {
      ...data,
      loading: true,
      success: false,
      error: null
    }),
  success: (data: any) =>
    createAction(EXAMPLE.SUCCESS, {
      ...data,
      loading: false,
      success: true,
      error: null
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
function* handleGet() {
  try {
    const { data } = yield call(
      axios.get,
      "https://jsonplaceholder.typicode.com/posts/"
    )
    yield put(dispatchExample.success({ data }))
  } catch (e) {
    yield put(dispatchExample.failure({ error: { ...e } }))
  }
}

function* handleGetOne(action: IAction<EXAMPLE, { id: number }>) {
  try {
    const { id } = action.payload
    const { data } = yield call(
      axios.get,
      `https://jsonplaceholder.typicode.com/posts/${id}`
    )
    yield put(dispatchExample.success({ data }))
  } catch (e) {
    yield put(dispatchExample.failure({ error: { ...e } }))
  }
}

function* handlePost(action: IAction<EXAMPLE, { saveData: string }>) {
  try {
    const { saveData } = action.payload
    const { data } = yield call(
      axios.post,
      "https://jsonplaceholder.typicode.com/posts/",
      { saveData }
    )
    yield put(dispatchExample.success({ data }))
  } catch (e) {
    yield put(dispatchExample.failure({ error: { ...e } }))
  }
}

function* handlePut(
  action: IAction<EXAMPLE, { id: number; updateData: string }>
) {
  try {
    const { id, updateData } = action.payload
    const { data } = yield call(
      axios.put,
      `https://jsonplaceholder.typicode.com/posts/${id}`,
      { updateData }
    )
    yield put(dispatchExample.success({ data }))
  } catch (e) {
    yield put(dispatchExample.failure({ error: { ...e } }))
  }
}

function* handlePatch(
  action: IAction<EXAMPLE, { id: number; updateData: string }>
) {
  try {
    const { id, updateData } = action.payload
    const { data } = yield call(
      axios.patch,
      `https://jsonplaceholder.typicode.com/posts/${id}`,
      { updateData }
    )
    yield put(dispatchExample.success({ data }))
  } catch (e) {
    yield put(dispatchExample.failure({ error: { ...e } }))
  }
}

function* handleDelete(action: IAction<EXAMPLE, { id: number }>) {
  try {
    const { id } = action.payload
    const { data } = yield call(
      axios.delete,
      `https://jsonplaceholder.typicode.com/posts/${id}`
    )
    yield put(dispatchExample.success({ data }))
  } catch (e) {
    yield put(dispatchExample.failure({ error: { ...e } }))
  }
}

export function* watchExampleSagas() {
  yield all([
    takeLatest(EXAMPLE.GET, handleGet),
    takeLatest(EXAMPLE.GET_ONE, handleGetOne),
    takeLatest(EXAMPLE.SAVE, handlePost),
    takeLatest(EXAMPLE.PUT, handlePut),
    takeLatest(EXAMPLE.PATCH, handlePatch),
    takeLatest(EXAMPLE.DELETE, handleDelete)
  ])
}

/**
 * Initial State
 * Reducer merges all changes from dispatched action objects on to this initial state
 */
const initialState = fromJS({
  query: "",
  urlTokenMetadata: [],
  ...defaultState.toJS()
})

/**
 * Duck Reducer
 * Merges dispatched action objects on to the existing (or initial) state to generate new state
 */
export default function ExampleReducer(
  state = initialState,
  action: IAction<string, {}>
) {
  switch (action.type) {
    case EXAMPLE.GET:
    case EXAMPLE.GET_ONE:
    case EXAMPLE.SAVE:
    case EXAMPLE.DELETE:
    case EXAMPLE.SUCCESS:
    case EXAMPLE.FAILURE:
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
export interface IExampleState extends IDefaultState {
  [key: string]: any
}
