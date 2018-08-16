import { IMiskAdminTab } from "@misk/common"
import {
  ADMINTABS, IActionType, ITEM, LOADTAB
} from "./types"
import { createAction, IAction } from "./utils"

export const dispatchItem = {
  delete: (id: number) => createAction(ITEM.DELETE, { id, loading: true, success: false, error: null }),
  failure: (error: any) => createAction(ITEM.FAILURE, { ...error, loading: false, success: false }),
  patch: (id: number, data: any) => createAction(ITEM.PATCH, { id, ...data, loading: true, success: false, error: null }),
  put: (id: number, data: any) => createAction(ITEM.PUT, { id, ...data, loading: true, success: false, error: null }),
  request: () => createAction(ITEM.GET, { loading: true, success: false, error: null }),
  requestOne: (id: number) => createAction(ITEM.GET_ONE, { id, loading: true, success: false, error: null }),
  save: (data: any) => createAction(ITEM.SAVE, { ...data, loading: true, success: false, error: null }),
  success: (data: any) => createAction(ITEM.SUCCESS, { ...data, loading: false, success: true, error: null }),
}

export const dispatchAdminTabs = {
  failure: (error: any) => createAction(ADMINTABS.FAILURE, { ...error, loading: false, success: false }),
  getAll: () => createAction(ADMINTABS.GET_ALL, { loading: true, success: false, error: null }),
  success: (data: any) => createAction(ADMINTABS.SUCCESS, { ...data, loading: false, success: true, error: null }),
}

export const dispatchLoadTab = {
  failure: (error: any) => createAction(LOADTAB.FAILURE, { ...error, loading: false, success: false }),
  getOne: (tab: IMiskAdminTab) => createAction(LOADTAB.GET_ONE, { tab, loading: true, success: false, error: null }),  
  success: (data: any) => createAction(LOADTAB.SUCCESS, { ...data, loading: false, success: true, error: null }),
}

export { ADMINTABS, IAction, IActionType, ITEM, LOADTAB }
