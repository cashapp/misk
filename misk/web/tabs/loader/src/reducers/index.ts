import { RouterState } from "connected-react-router"
import { combineReducers } from "redux"
import { IAdminTabs } from "../Loader"
import adminTabsReducer from "./adminTabs"
import itemReducer from "./item"

const rootReducer = combineReducers({
  adminTabs: adminTabsReducer,
  item: itemReducer
})

export interface IState {
  adminTabs: IAdminTabs
  item: any
  router: RouterState
}

export default rootReducer
