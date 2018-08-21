import {
  CONFIG, IActionType
} from "./types"
import { createAction, IAction } from "./utils"

export const dispatchConfig = {
  failure: (error: any) => createAction(CONFIG.FAILURE, { ...error, loading: false, success: false }),
  getAll: () => createAction(CONFIG.GET_ALL, { loading: true, success: false, error: null }),
  success: (data: any) => createAction(CONFIG.SUCCESS, { ...data, loading: false, success: true, error: null }),
}

export { CONFIG, IAction, IActionType }
