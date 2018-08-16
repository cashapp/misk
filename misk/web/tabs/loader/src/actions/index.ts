import { IMiskAdminTab, IMiskAdminTabs } from "@misk/common"
import {
  IActionType, ITEM, LOADER
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

export const dispatchLoader = {
  failure: (error: any) => createAction(LOADER.FAILURE, { ...error, loading: false, success: false }),
  getAllComponents: () => createAction(LOADER.GET_ALL_COMPONENTS, { loading: true, success: false, error: null }),  
  getAllTabs: () => createAction(LOADER.GET_ALL_TABS, { loading: true, success: false, error: null }),
  getOneComponent: (tab: IMiskAdminTab) => createAction(LOADER.GET_ONE_COMPONENTS, { tab, loading: true, success: false, error: null }),  
  success: (data: any) => createAction(LOADER.SUCCESS, { ...data, loading: false, success: true, error: null }),
}

export { IAction, IActionType, ITEM, LOADER }
