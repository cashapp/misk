import { IMiskAdminTabs } from "@misk/common"
import { RouterState } from "connected-react-router"
import { combineReducers } from "redux"
import adminTabsReducer from "./adminTabs"
import itemReducer from "./item"

const rootReducer = combineReducers({
  adminTabs: adminTabsReducer,
  item: itemReducer
})

export interface IState {
  adminTabs: IMiskAdminTabs
  item: any
  router: RouterState
}

export default rootReducer
