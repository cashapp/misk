import { IMiskAdminTabs } from "@misk/common"
import { RouterState } from "connected-react-router"
import { fromJS, List } from "immutable"
import { combineReducers } from "redux"
import ConfigReducer from "./ConfigReducer"
import ItemReducer from "./ItemReducer"
import LoadTabReducer from "./LoadTabReducer"

export const defaultInitialState = fromJS({
  data: List([]),
  error: null,
  loading: false,
  success: false,
})

const rootReducer = combineReducers({
  config: ConfigReducer,
  item: ItemReducer,
  loader: LoadTabReducer
})

export interface IState {
  config: any
  item: any
  loader: any
  router: RouterState
}

export default rootReducer
