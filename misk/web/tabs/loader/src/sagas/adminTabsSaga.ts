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
  ADMINTABS, dispatchAdminTabs
} from "../actions"

function * handleGetAll () {
  try {
    const { data } = yield call(axios.get, "http://0.0.0.0:8080/api/admintab/all")
    const { adminTabs } = data
    // yield put(dispatchAdminTabs.success({ tabs: adminTabs }))
    yield put(dispatchAdminTabs.success({ adminTabs }))
  } catch (e) {
    yield put(dispatchAdminTabs.failure({ error: { ...e } }))
  }
}

function * watchAdminTabsSagas () {
  yield all([
    takeLatest(ADMINTABS.GET_ALL, handleGetAll),
  ])
}

export default watchAdminTabsSagas
