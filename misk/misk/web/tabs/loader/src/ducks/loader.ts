import { createAction, IMiskAction, IMiskAdminTab } from "@misk/common"
import axios from "axios"
import { fromJS, Map } from "immutable"
import { all, call, put, takeEvery, takeLatest } from "redux-saga/effects"

/**
 * Actions
 */
interface IActionType {
  LOADER: LOADER
}

export enum LOADER {
  FAILURE = "LOADER_FAILURE",
  GET_ALL_TABS = "LOADER_GET_ALL_TABS",
  GET_ONE_COMPONENT = "LOADER_GET_ONE_COMPONENT",
  SUCCESS = "LOADER_SUCCESS"
}

export const dispatchLoader = {
  failure: (error: any) => createAction(LOADER.FAILURE, { ...error, loading: false, success: false }),
  getAllTabs: (url: string) => createAction(LOADER.GET_ALL_TABS, { url, loading: true, success: false, error: null }),
  getOneComponent: (tab: IMiskAdminTab) => createAction(LOADER.GET_ONE_COMPONENT, { tab, loading: true, success: false, error: null }),  
  success: (data: any) => createAction(LOADER.SUCCESS, { ...data, loading: false, success: true, error: null }),
}

/**
 * Reducer
 * @param state 
 * @param action 
 */
export interface ILoaderState {
  adminTabComponents: {
    [tab:string]: string
  }
  adminTabs: IMiskAdminTab[]
  staleTabCache: boolean
  toJS: () => any
}

const initialState = fromJS({
  data: Map(),
  error: null,
  loading: false,
  success: false,
})

export default function loaderReducer (state = initialState, action: IMiskAction<string, {}>) {
  switch (action.type) {
    case LOADER.FAILURE:
    case LOADER.GET_ONE_COMPONENT:
    case LOADER.GET_ALL_TABS:
    case LOADER.SUCCESS:
      return state.mergeDeep(action.payload)
    default:
      return state
  }
}

/**
 * Sagas
 */

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

export function * watchLoaderSagas () {
  yield all([
    takeEvery(LOADER.GET_ONE_COMPONENT, handleGetOneComponent),
    takeLatest(LOADER.GET_ALL_TABS, handleGetAllTabs),
  ])
}
