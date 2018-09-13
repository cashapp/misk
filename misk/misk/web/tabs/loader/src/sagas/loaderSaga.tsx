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

function * handleCacheTabEntries (action: IAction<IActionType, { MiskBinder: any}>) {
  const { MiskBinder } = action.payload
    yield put(dispatchLoader.success({ adminTabComponents: { blah: "blah"} , staleTabCache: false }))
    if (MiskBinder) {
      yield put(dispatchLoader.success({ adminTabComponents: MiskBinder.TabEntry, staleTabCache: false }))
    } else {
      yield put(dispatchLoader.failure({ staleTabCache: true }))
    }
}

function * handleGetAllTabs (action: IAction<IActionType, { url: string}>) {
  const { url } = action.payload
  try {
    const { data } = yield call(axios.get, url)
    const { adminTabs, adminTabCategories } = data
    yield put(dispatchLoader.success({ adminTabs, adminTabCategories }))
  } catch (e) {
    yield put(dispatchLoader.failure({ error: { ...e } }))
  }
}

function * handleGetAllAndTabs (action: IAction<IActionType, { url: string}>) {
  const { url } = action.payload
  let adminTabs: any = {}
  let adminTabCategories: any = {}
  try {
    yield put(dispatchLoader.getAllTabs(url))
    const { data } = yield call(axios.get, url)
    adminTabs = data.adminTabs
    adminTabCategories = data.adminTabCategories
    yield put(dispatchLoader.success({ adminTabs, adminTabCategories }))
    if (Object.entries(adminTabs).length === 0) {
      yield put(dispatchLoader.success({ adminTabComponents: { } }))
    }
  } catch (e) {
    yield put(dispatchLoader.failure({ error: { ...e } }))
  }

  for (const key in adminTabs) {
    if (adminTabs.hasOwnProperty(key)) {
      const tab = adminTabs[key]
      try {
        const { data } = yield call(axios.get, `/_tab/${tab.slug}/tab_${tab.slug}.js`)
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
