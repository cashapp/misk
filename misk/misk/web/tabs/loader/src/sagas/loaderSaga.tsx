// In case you need to use a selector
// import also select from redux-saga/effects
// and then simplie yield select(yourSelector())
//
// In case you need to redirect to whatever route
// import { push } from react-router-redux and then
// yield put(push('/next-page'))

import { IMiskAdminTab } from "@misk/common"
import axios from "axios"
import { all, call, put, takeEvery, takeLatest } from "redux-saga/effects"

import {
  dispatchLoader, IAction, IActionType, LOADER
} from "../actions"
import { IMultibinder } from "../utils/binder"

function * handleCacheTabEntries (action: IAction<IActionType, { MiskBinder: IMultibinder}>) {
  const { MiskBinder } = action.payload
    yield put(dispatchLoader.success({ adminTabComponents: { blah: "blah"} , staleTabCache: false }))
    if (MiskBinder) {
      yield put(dispatchLoader.success({ adminTabComponents: MiskBinder.TabEntry, staleTabCache: false }))
    } else {
      yield put(dispatchLoader.failure({ staleTabCache: true }))
    }
}

function * handleGetAllTabs () {
  try {
    const { data } = yield call(axios.get, "/api/admintab/all")
    const { adminTabs } = data
    yield put(dispatchLoader.success({ adminTabs }))
  } catch (e) {
    yield put(dispatchLoader.failure({ error: { ...e } }))
  }
}

function * handleGetAllAndTabs () {
  let adminTabs: any = {}
  try {
    yield put(dispatchLoader.getAllTabs())
    const { data } = yield call(axios.get, "/api/admintab/all")
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
      const url = `/_tab/${tab.slug}/tab_${tab.slug}.js`
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
  const url = `/_tab/${tab.slug}/tab_${tab.slug}.js`
  try {
    const { data } = yield call(axios.get, url)
    yield put(dispatchLoader.success({ adminTabComponents: { [tab.slug]: data } }))
  } catch (e) {
    yield put(dispatchLoader.failure({ error: { ...e } }))
  }
}

function * handleRegisterComponent (action: IAction<IActionType, { name: string, Component: any }>) {
  const { name } = action.payload
  const { Component } = action.payload
  yield put(dispatchLoader.success({ adminTabComponents: { [name]: Component } }))
}

function * watchLoaderSagas () {
  yield all([
    takeLatest(LOADER.CACHE_TAB_ENTRIES, handleCacheTabEntries),
    takeEvery(LOADER.GET_ONE_COMPONENT, handleGetOneComponent),
    takeLatest(LOADER.GET_ALL_TABS, handleGetAllTabs),
    takeLatest(LOADER.GET_ALL_COMPONENTS_AND_TABS, handleGetAllAndTabs),
    takeEvery(LOADER.REGISTER_COMPONENT, handleRegisterComponent),
  ])
}

export default watchLoaderSagas
