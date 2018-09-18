// In case you need to use a selector
// import also select from redux-saga/effects
// and then simplie yield select(yourSelector())
//
// In case you need to redirect to whatever route
// import { push } from react-router-redux and then
// yield put(push('/next-page'))

import { IMiskAction } from "@misk/common"
import axios from "axios"
import { all, call, put, takeLatest } from "redux-saga/effects"
const dayjs = require("dayjs")

import {
  CONFIG, dispatchConfig, IActionType
} from "../actions"

const dateFormat = "YYYY-MM-DD HH:mm:ss"

function * handleGetAll (action: IMiskAction<IActionType, { url: string}>) {
  const { url } = action.payload
  const resources: any = []
  let data: any = {}
  try {
    const response = yield call(axios.get, url)
    data = response.data

    resources.push({name: "live-config.yaml", file: data.effective_config})
    Object.entries(data.yaml_files).forEach(([key,value]) => {
      resources.push({name: key, file: value})
    })
    yield put(dispatchConfig.success({
      data,
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

function * watchConfigSagas () {
  yield all([
    takeLatest(CONFIG.GET_ALL, handleGetAll)
  ])
}

export default watchConfigSagas
