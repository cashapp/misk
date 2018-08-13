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
  IAction, IActionType, LOADER, loader
} from "../actions"

function * handleGetAdminTabs () {
  try {
    const { data } = yield call(axios.get, "http://0.0.0.0:8080/api/admintab/all")
    yield put(loader.success({ data }))
  } catch (e) {
    yield put(loader.failure({ error: { ...e } }))
  }
}

function * watchAdminTabsSagas () {
  yield all([
    takeLatest(LOADER.GET_ADMINTABS, handleGetAdminTabs),
  ])
}

export default watchAdminTabsSagas
