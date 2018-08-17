// In case you need to use a selector
// import also select from redux-saga/effects
// and then simplie yield select(yourSelector())
//
// In case you need to redirect to whatever route
// import { push } from react-router-redux and then
// yield put(push('/next-page'))

import { IMiskAdminTab, IMiskAdminTabs } from "@misk/common"
import axios from "axios"
import { all, call, put, takeEvery, takeLatest } from "redux-saga/effects"

import {
  dispatchLoader, IAction, IActionType, LOADER
} from "../actions"

function * handleGetAllTabs () {
  try {
    const { data } = yield call(axios.get, "http://0.0.0.0:8080/api/admintab/all")
    const { adminTabs } = data
    yield put(dispatchLoader.success({ adminTabs }))
  } catch (e) {
    yield put(dispatchLoader.failure({ error: { ...e } }))
  }
}

function * handleGetAllAndTabs (action: IAction<IActionType, {}>) {
  let adminTabs: any = {}
  try {
    yield put(dispatchLoader.getAllTabs())
    const { data } = yield call(axios.get, "http://0.0.0.0:8080/api/admintab/all")
    adminTabs = data.adminTabs
    yield put(dispatchLoader.success({ adminTabs }))
    if (Object.entries(adminTabs).length === 0) {
      yield put(dispatchLoader.success({ adminTabComponents: { } }))
    }
  } catch (e) {
    yield put(dispatchLoader.failure({ error: { ...e } }))
  }

  for (const key in adminTabs) {
    if (adminTabs.hasOwnProperty(key)) {
      const tab = adminTabs[key]
      const url = `http://0.0.0.0:8080/_tab/${tab.slug}/tab_${tab.slug}.js`
      try {
        const { data } = yield call(axios.get, url)
        yield put(dispatchLoader.success({ adminTabComponents: { [tab.slug]: data } }))
      } catch (e) {
        yield put(dispatchLoader.failure({ error: { ...e } }))
      }
    }
  }
}

function * handleGetOneComponent (action: IAction<IActionType, { tab: IMiskAdminTab }>) {
  const { tab } = action.payload
  const url = `http://0.0.0.0:8080/_tab/${tab.slug}/tab_${tab.slug}.js`
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
    takeLatest(LOADER.GET_ALL_COMPONENTS_AND_TABS, handleGetAllAndTabs),
  ])
}

export default watchLoaderSagas
