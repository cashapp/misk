import { createActionTypes } from "./utils"

export interface IActionType {
  [base:string]: string
}

export const ITEM = createActionTypes("ITEM", [
  "GET",
  "GET_ONE",
  "SAVE",
  "PUT",
  "PATCH",
  "DELETE",
  "SUCCESS",
  "FAILURE"
])

export const LOADER = createActionTypes("LOADER", [
  "FAILURE",
  "GET_ALL_COMPONENTS_AND_TABS",
  "GET_ALL_TABS",
  "GET_ONE_COMPONENT",
  "SUCCESS"
])