import {
  createAction,
  defaultState,
  IAction,
  IDashboardTab,
  IDefaultState,
  IServiceMetadata
} from "@misk/common"
import axios from "axios"
import { fromJS } from "immutable"
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
  GET_SERVICE_METADATA = "LOADER_GET_SERVICE_METADATA",
  SUCCESS = "LOADER_SUCCESS"
}

export const dispatchLoader = {
  failure: (error: any) =>
    createAction(LOADER.FAILURE, { ...error, loading: false, success: false }),
  getAllTabs: (url: string) =>
    createAction(LOADER.GET_ALL_TABS, {
      url,
      loading: true,
      success: false,
      error: null
    }),
  getOneComponent: (tab: IDashboardTab) =>
    createAction(LOADER.GET_ONE_COMPONENT, {
      tab,
      loading: true,
      success: false,
      error: null
    }),
  getServiceMetadata: (url: string) =>
    createAction(LOADER.GET_SERVICE_METADATA, {
      url,
      loading: true,
      success: false,
      error: null
    }),
  success: (data: any) =>
    createAction(LOADER.SUCCESS, {
      ...data,
      loading: false,
      success: true,
      error: null
    })
}

/**
 * Reducer
 * @param state
 * @param action
 */
export interface ILoaderState extends IDefaultState {
  adminTabComponents: {
    [tab: string]: string
  }
  adminDashboardTabs: IDashboardTab[]
  serviceMetadata: IServiceMetadata
}

const initialState = fromJS({
  adminDashboardTabs: [],
  adminTabComponents: {},
  serviceMetadata: {},
  ...defaultState.toJS()
})

export default function loaderReducer(
  state = initialState,
  action: IAction<string, {}>
) {
  switch (action.type) {
    case LOADER.FAILURE:
    case LOADER.GET_ONE_COMPONENT:
    case LOADER.GET_ALL_TABS:
    case LOADER.GET_SERVICE_METADATA:
    case LOADER.SUCCESS:
      return state.mergeDeep(action.payload)
    default:
      return state
  }
}

/**
 * Sagas
 */

function* handleGetAllTabs(action: IAction<IActionType, { url: string }>) {
  const { url } = action.payload
  try {
    const { data } = yield call(axios.get, url)
    const { adminDashboardTabs } = data
    yield put(dispatchLoader.success({ adminDashboardTabs }))
  } catch (e) {
    yield put(dispatchLoader.failure({ error: { ...e } }))
  }
}

function* handleGetOneComponent(
  action: IAction<IActionType, { tab: IDashboardTab }>
) {
  const { tab } = action.payload
  const url = `/_tab/${tab.slug}/tab_${tab.slug}.js`
  try {
    const { data } = yield call(axios.get, url)
    yield put(
      dispatchLoader.success({ adminTabComponents: { [tab.slug]: data } })
    )
  } catch (e) {
    yield put(dispatchLoader.failure({ error: { ...e } }))
  }
}

function* handleGetServiceMetadata(
  action: IAction<IActionType, { url: string }>
) {
  const { url } = action.payload
  try {
    const { data } = yield call(axios.get, url)
    const { serviceMetadata } = data
    yield put(dispatchLoader.success({ serviceMetadata }))
  } catch (e) {
    yield put(dispatchLoader.failure({ error: { ...e } }))
  }
}

export function* watchLoaderSagas() {
  yield all([
    takeEvery(LOADER.GET_ONE_COMPONENT, handleGetOneComponent),
    takeLatest(LOADER.GET_ALL_TABS, handleGetAllTabs),
    takeLatest(LOADER.GET_SERVICE_METADATA, handleGetServiceMetadata)
  ])
}
