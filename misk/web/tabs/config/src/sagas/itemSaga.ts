// In case you need to use a selector
// import also select from redux-saga/effects
// and then simplie yield select(yourSelector())
//
// In case you need to redirect to whatever route
// import { push } from react-router-redux and then
// yield put(push('/next-page'))

import axios from "axios"
import { all, call, put, takeLatest } from "redux-saga/effects"

import {
  dispatchItem, IAction, IActionType, ITEM
} from "../actions"

function * handleGet () {
  try {
    const { data } = yield call(axios.get, "https://jsonplaceholder.typicode.com/posts/")
    yield put(dispatchItem.success({ data }))
  } catch (e) {
    yield put(dispatchItem.failure({ error: { ...e } }))
  }
}

function * handleGetOne (action: IAction<IActionType, {id: number}>) {
  try {
    const { id } = action.payload
    const { data } = yield call(axios.get, `https://jsonplaceholder.typicode.com/posts/${id}`)
    yield put(dispatchItem.success({ data }))
  } catch (e) {
    yield put(dispatchItem.failure({ error: { ...e } }))
  }
}

function * handlePost (action: IAction<IActionType, {saveData: string}>) {
  try {
    const { saveData } = action.payload
    const { data } = yield call(axios.post, "https://jsonplaceholder.typicode.com/posts/", { saveData })
    yield put(dispatchItem.success({ data }))
  } catch (e) {
    yield put(dispatchItem.failure({ error: { ...e } }))
  }
}

function * handlePut (action: IAction<IActionType, {id: number, updateData: string}>) {
  try {
    const { id, updateData } = action.payload
    const { data } = yield call(axios.put, `https://jsonplaceholder.typicode.com/posts/${id}`, { updateData })
    yield put(dispatchItem.success({ data }))
  } catch (e) {
    yield put(dispatchItem.failure({ error: { ...e } }))
  }
}

function * handlePatch (action: IAction<IActionType, {id: number, updateData: string}>) {
  try {
    const { id, updateData } = action.payload
    const { data } = yield call(axios.patch, `https://jsonplaceholder.typicode.com/posts/${id}`, { updateData })
    yield put(dispatchItem.success({ data }))
  } catch (e) {
    yield put(dispatchItem.failure({ error: { ...e } }))
  }
}

function * handleDelete (action: IAction<IActionType, {id: number}>) {
  try {
    const { id } = action.payload
    const { data } = yield call(axios.delete, `https://jsonplaceholder.typicode.com/posts/${id}`)
    yield put(dispatchItem.success({ data }))
  } catch (e) {
    yield put(dispatchItem.failure({ error: { ...e } }))
  }
}

function * watchItemSagas () {
  yield all([
    takeLatest(ITEM.GET, handleGet),
    takeLatest(ITEM.GET_ONE, handleGetOne),
    takeLatest(ITEM.SAVE, handlePost),
    takeLatest(ITEM.PUT, handlePut),
    takeLatest(ITEM.PATCH, handlePatch),
    takeLatest(ITEM.DELETE, handleDelete)
  ])
}

export default watchItemSagas
