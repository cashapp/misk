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

const dateFormat = "YYYY-MM-DD HH:mm:ss"

function * handleGetAll () {
  const files: any = []
  let data: any = {}
  try {
    const response = yield call(axios.get, "http://localhost:8080/api/config/all")
    data = response.data
  } catch (e) {
    yield put(dispatchConfig.failure({ 
      error: { ...e },
      status: `Offline. Last attemped update ${dayjs().format(dateFormat)}`
     }))
  }

  try {
    files.push({name: "live-config.yaml", file: data.effective_config})
    Object.entries(data.yaml_files).forEach(([key,value]) => {
      files.push({name: key, file: value})
    })
    yield put(dispatchConfig.success({ 
      data,
      files,
      lastOnline: dayjs().format(dateFormat),
      status: `Online as of: ${dayjs().format(dateFormat)}`
      }))
  } catch (e) {
    yield put(dispatchConfig.failure({ 
      data,
      error: { ...e, msg: "config yaml parse error" },
      files,
      lastOnline: dayjs().format(dateFormat),
      status: `Online as of: ${dayjs().format(dateFormat)}`
      }))
  }
  
  
}

function * watchConfigSagas () {
  yield all([
    takeLatest(CONFIG.GET_ALL, handleGetAll)
  ])
}

export default watchConfigSagas
