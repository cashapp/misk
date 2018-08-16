import { all, fork } from "redux-saga/effects"
import watchAdminTabsSagas from "./adminTabsSaga"
import watchItemSagas from "./itemSaga"
import watchLoadTabSagas from "./loadTabSaga"

export default function * rootSaga () {
  yield all([
    fork(watchAdminTabsSagas),
    fork(watchItemSagas),
    fork(watchLoadTabSagas)
  ])
}
