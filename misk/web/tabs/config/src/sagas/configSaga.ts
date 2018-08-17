// In case you need to use a selector
// import also select from redux-saga/effects
// and then simplie yield select(yourSelector())
//
// In case you need to redirect to whatever route
// import { push } from react-router-redux and then
// yield put(push('/next-page'))

import axios from "axios"
import { all, call, put, takeLatest } from "redux-saga/effects"
const dayjs = require("dayjs")

import {
  CONFIG, dispatchConfig, IAction, IActionType
} from "../actions"

function * handleGetAll () {
  const files: any = []
  try {
    const { data } = yield call(axios.get, "http://localhost:8080/_admin/api/config/all")
    files.push({name: "live-config.yaml", file: this.toYaml(data.effective_config)})
    Object.entries(data.yaml_files).forEach(([key,value]) => {
      files.push({name: key, file: value})
    })
    yield put(dispatchConfig.success({ 
      files,
      lastOnline: dayjs().format("YYYY-MM-DD HH:mm:ss:SSS"),
      status: `Online as of: ${dayjs().format("YYYY-MM-DD HH:mm:ss")}`
     }))
  } catch (e) {
    yield put(dispatchConfig.failure({ 
      error: { ...e },
      status: `Offline. Last attemped update ${dayjs().format("YYYY-MM-DD HH:mm:ss")}`
     }))
  }
}

function * watchConfigSagas () {
  yield all([
    takeLatest(CONFIG.GET_ALL, handleGetAll),
  ])
}

export default watchConfigSagas
