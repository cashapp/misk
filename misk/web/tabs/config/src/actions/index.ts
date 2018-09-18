import { createAction, IMiskAction } from "@misk/common"
import {
  CONFIG, IActionType
} from "./types"

export const dispatchConfig = {
  failure: (error: any) => createAction(CONFIG.FAILURE, { ...error, loading: false, success: false }),
  getAll: (url: string) => createAction(CONFIG.GET_ALL, { url, loading: true, success: false, error: null }),
  success: (data: any) => createAction(CONFIG.SUCCESS, { ...data, loading: false, success: true, error: null }),
}

export { CONFIG, IMiskAction, IActionType }
