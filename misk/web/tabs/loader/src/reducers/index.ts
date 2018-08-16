import { IMiskAdminTabs } from "@misk/common"
import { RouterState } from "connected-react-router"
import { fromJS, List } from "immutable"
import { combineReducers } from "redux"
import AdminTabsReducer from "./AdminTabsReducer"
import ItemReducer from "./ItemReducer"
import LoadTabReducer from "./LoadTabReducer"

export const defaultInitialState = fromJS({
  data: List([]),
  error: null,
  loading: false,
  success: false,
})

const rootReducer = combineReducers({
  adminTabs: AdminTabsReducer,
  item: ItemReducer,
  loadTab: LoadTabReducer
})

export interface IState {
  adminTabs: IMiskAdminTabs
  item: any
  loadTab: any
  router: RouterState
}

export default rootReducer
