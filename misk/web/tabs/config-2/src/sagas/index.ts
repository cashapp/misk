import { all, fork } from "redux-saga/effects"
import watchConfigSagas from "./configSaga"

export default function * rootSaga () {
  yield all([
    fork(watchConfigSagas)
  ])
}
