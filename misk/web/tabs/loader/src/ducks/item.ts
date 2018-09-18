import { createAction, IMiskAction } from "@misk/common"
import axios from "axios"
import { fromJS, List } from "immutable"
import { all, call, put, takeLatest } from "redux-saga/effects"

/**
 * Actions
 */
interface IActionType {
  ITEM: ITEM
}

export enum ITEM {
  GET = "ITEM_GET",
  GET_ONE = "ITEM_GET_ONE",
  SAVE = "ITEM_SAVE",
  PUT = "ITEM_PUT",
  PATCH = "ITEM_PATCH",
  DELETE = "ITEM_DELETE",
  SUCCESS = "ITEM_SUCCESS",
  FAILURE = "ITEM_FAILURE"
}

export const dispatchItem = {
  delete: (id: number) => createAction(ITEM.DELETE, { id, loading: true, success: false, error: null }),
  failure: (error: any) => createAction(ITEM.FAILURE, { ...error, loading: false, success: false }),
  patch: (id: number, data: any) => createAction(ITEM.PATCH, { id, ...data, loading: true, success: false, error: null }),
  put: (id: number, data: any) => createAction(ITEM.PUT, { id, ...data, loading: true, success: false, error: null }),
  request: () => createAction(ITEM.GET, { loading: true, success: false, error: null }),
  requestOne: (id: number) => createAction(ITEM.GET_ONE, { id, loading: true, success: false, error: null }),
  save: (data: any) => createAction(ITEM.SAVE, { ...data, loading: true, success: false, error: null }),
  success: (data: any) => createAction(ITEM.SUCCESS, { ...data, loading: false, success: true, error: null }),
}

/**
 * Reducer
 * @param state 
 * @param action 
 */
export interface IItemState {
  [key: string]: any
}

const initialState = fromJS({
  data: List([]),
  error: null,
  loading: false,
  success: false,
})

export default function ItemReducer (state = initialState, action: IMiskAction<string, {}>) {
  switch (action.type) {
    case ITEM.GET:
    case ITEM.GET_ONE:
    case ITEM.SAVE:
    case ITEM.DELETE:
    case ITEM.SUCCESS:
    case ITEM.FAILURE:
      return state.merge(action.payload)
    default:
      return state
  }
}

/**
 * Sagas
 */

function * handleGet () {
  try {
    const { data } = yield call(axios.get, "https://jsonplaceholder.typicode.com/posts/")
    yield put(dispatchItem.success({ data }))
  } catch (e) {
    yield put(dispatchItem.failure({ error: { ...e } }))
  }
}

function * handleGetOne (action: IMiskAction<IActionType, {id: number}>) {
  try {
    const { id } = action.payload
    const { data } = yield call(axios.get, `https://jsonplaceholder.typicode.com/posts/${id}`)
    yield put(dispatchItem.success({ data }))
  } catch (e) {
    yield put(dispatchItem.failure({ error: { ...e } }))
  }
}

function * handlePost (action: IMiskAction<IActionType, {saveData: string}>) {
  try {
    const { saveData } = action.payload
    const { data } = yield call(axios.post, "https://jsonplaceholder.typicode.com/posts/", { saveData })
    yield put(dispatchItem.success({ data }))
  } catch (e) {
    yield put(dispatchItem.failure({ error: { ...e } }))
  }
}

function * handlePut (action: IMiskAction<IActionType, {id: number, updateData: string}>) {
  try {
    const { id, updateData } = action.payload
    const { data } = yield call(axios.put, `https://jsonplaceholder.typicode.com/posts/${id}`, { updateData })
    yield put(dispatchItem.success({ data }))
  } catch (e) {
    yield put(dispatchItem.failure({ error: { ...e } }))
  }
}

function * handlePatch (action: IMiskAction<IActionType, {id: number, updateData: string}>) {
  try {
    const { id, updateData } = action.payload
    const { data } = yield call(axios.patch, `https://jsonplaceholder.typicode.com/posts/${id}`, { updateData })
    yield put(dispatchItem.success({ data }))
  } catch (e) {
    yield put(dispatchItem.failure({ error: { ...e } }))
  }
}

function * handleDelete (action: IMiskAction<IActionType, {id: number}>) {
  try {
    const { id } = action.payload
    const { data } = yield call(axios.delete, `https://jsonplaceholder.typicode.com/posts/${id}`)
    yield put(dispatchItem.success({ data }))
  } catch (e) {
    yield put(dispatchItem.failure({ error: { ...e } }))
  }
}

export function * watchItemSagas () {
  yield all([
    takeLatest(ITEM.GET, handleGet),
    takeLatest(ITEM.GET_ONE, handleGetOne),
    takeLatest(ITEM.SAVE, handlePost),
    takeLatest(ITEM.PUT, handlePut),
    takeLatest(ITEM.PATCH, handlePatch),
    takeLatest(ITEM.DELETE, handleDelete)
  ])
}
