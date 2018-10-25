import { createAction, defaultState, IAction, IDefaultState } from "@misk/common"
import axios from "axios"
import { fromJS, Map } from "immutable"
import { all, call, put, takeLatest } from "redux-saga/effects"
const dayjs = require("dayjs")

/**
 * Actions
 */
interface IActionType {
  CONFIG: CONFIG
}

export enum CONFIG {
  FAILURE = "CONFIG_FAILURE",
  GET_ALL = "CONFIG_GET_ALL",
  SUCCESS = "CONFIG_SUCCESS"
}

export const dispatchConfig = {
  failure: (error: any) => createAction(CONFIG.FAILURE, { ...error, loading: false, success: false }),
  getAll: (url: string) => createAction(CONFIG.GET_ALL, { url, loading: true, success: false, error: null }),
  success: (data: any) => createAction(CONFIG.SUCCESS, { ...data, loading: false, success: true, error: null }),
}

/**
 * Reducer
 * @param state 
 * @param action 
 */
export interface IConfigResources {
  [name: string]: string
}

export interface IConfigState extends IDefaultState {
  resources: IConfigResources
  status: string
}

const initialState = fromJS({
  data: Map(),
  query: "",
  urlTokenMetadata: [],
  ...defaultState.toJS()
})

export default function ConfigReducer (state = initialState, action: IAction<string, {}>) {
  switch (action.type) {
    case CONFIG.GET_ALL:
    case CONFIG.SUCCESS:
    case CONFIG.FAILURE:
      return state.merge(action.payload)
    default:
      return state
  }
}

/**
 * Sagas
 */
const dateFormat = "YYYY-MM-DD HH:mm:ss"

function * handleGetAll (action: IAction<IActionType, { url: string}>) {
  const { url } = action.payload
  try {
    const response = yield call(axios.get, url)
    const { resources } = response.data
    yield put(dispatchConfig.success({
      lastOnline: dayjs().format(dateFormat),
      resources,
      status: `Online as of: ${dayjs().format(dateFormat)}`
      }))
  } catch (e) {
    yield put(dispatchConfig.failure({ 
      error: { ...e }
     }))
  }
}

export function * watchConfigSagas () {
  yield all([
    takeLatest(CONFIG.GET_ALL, handleGetAll)
  ])
}

