import { all, fork } from "redux-saga/effects"
import watchAdminTabsSagas from "./adminTabsSaga"
import watchItemSagas from "./itemSaga"

export default function * rootSaga () {
  yield all([
    fork(watchAdminTabsSagas),
    fork(watchItemSagas)
  ])
}
