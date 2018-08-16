import { all, fork } from "redux-saga/effects"
import watchItemSagas from "./itemSaga"
import watchLoaderSagas from "./loaderSaga"

export default function * rootSaga () {
  yield all([
    fork(watchItemSagas),
    fork(watchLoaderSagas)
  ])
}
