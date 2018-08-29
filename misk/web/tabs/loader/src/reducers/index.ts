import { IMiskAdminTabs } from "@misk/common"
import { RouterState } from "connected-react-router"
import { fromJS, List } from "immutable"
import { combineReducers } from "redux"
import ItemReducer from "./ItemReducer"
import LoadTabReducer from "./LoadTabReducer"

export const defaultInitialState = fromJS({
  data: List([]),
  error: null,
  loading: false,
  success: false,
})

const rootReducer = combineReducers({
  item: ItemReducer,
  loader: LoadTabReducer
})

export interface ILoaderState {
  adminTabComponents: {
    [tab:string]: string
  }
  adminTabs: IMiskAdminTabs
  staleTabCache: boolean
  toJS: () => any
}

export interface IState {
  item: any
  loader: ILoaderState
  router: RouterState
}

export default rootReducer
