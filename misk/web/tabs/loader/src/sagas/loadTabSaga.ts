// In case you need to use a selector
// import also select from redux-saga/effects
// and then simplie yield select(yourSelector())
//
// In case you need to redirect to whatever route
// import { push } from react-router-redux and then
// yield put(push('/next-page'))

import { IMiskAdminTab } from "@misk/common"
import axios from "axios"
import { all, call, put, takeLatest } from "redux-saga/effects"

import {
  dispatchLoadTab, IAction, IActionType, LOADTAB
} from "../actions"

function * handleGetOne (action: IAction<IActionType, {tab: IMiskAdminTab}>) {
  try {
    const { tab } = action.payload
    const url = `${tab.url_path_prefix}/tab_${tab.slug}.js`
    const { data } = yield call(axios.get, url)
    yield put(dispatchLoadTab.success({ data }))
  } catch (e) {
    yield put(dispatchLoadTab.failure({ error: { ...e } }))
  }
}

function * watchLoadTabSagas () {
  yield all([
    takeLatest(LOADTAB.GET_ONE, handleGetOne),
  ])
}

export default watchLoadTabSagas
