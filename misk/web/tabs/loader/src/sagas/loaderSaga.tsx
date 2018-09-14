// In case you need to use a selector
// import also select from redux-saga/effects
// and then simplie yield select(yourSelector())
//
// In case you need to redirect to whatever route
// import { push } from react-router-redux and then
// yield put(push('/next-page'))

import { IMiskAction, IMiskAdminTab } from "@misk/common"
import axios from "axios"
import { all, call, put, takeEvery, takeLatest } from "redux-saga/effects"

import {
  dispatchLoader, IActionType, LOADER
} from "../actions"

function * handleGetAllTabs (action: IMiskAction<IActionType, { url: string}>) {
  const { url } = action.payload
  try {
    const { data } = yield call(axios.get, url)
    const { adminTabs, adminTabCategories } = data
    yield put(dispatchLoader.success({ adminTabs, adminTabCategories }))
  } catch (e) {
    yield put(dispatchLoader.failure({ error: { ...e } }))
  }
}

function * handleGetOneComponent (action: IMiskAction<IActionType, { tab: IMiskAdminTab }>) {
  const { tab } = action.payload
  const url = `/_tab/${tab.slug}/tab_${tab.slug}.js`
  try {
    const { data } = yield call(axios.get, url)
    yield put(dispatchLoader.success({ adminTabComponents: { [tab.slug]: data } }))
  } catch (e) {
    yield put(dispatchLoader.failure({ error: { ...e } }))
  }
}

function * watchLoaderSagas () {
  yield all([
    takeEvery(LOADER.GET_ONE_COMPONENT, handleGetOneComponent),
    takeLatest(LOADER.GET_ALL_TABS, handleGetAllTabs),
  ])
}

export default watchLoaderSagas
