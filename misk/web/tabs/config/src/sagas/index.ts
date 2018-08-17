import { all, fork } from "redux-saga/effects"
import watchConfigSagas from "./configSaga"
import watchItemSagas from "./itemSaga"
import watchLoaderSagas from "./loaderSaga"

export default function * rootSaga () {
  yield all([
    fork(watchConfigSagas),
    fork(watchItemSagas),
    fork(watchLoaderSagas)
  ])
}
