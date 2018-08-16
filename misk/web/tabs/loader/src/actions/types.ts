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

export const ADMINTABS = createActionTypes("ADMINTABS", [
  "FAILURE",
  "GET_ALL",
  "SUCCESS"
])

export const LOADTAB = createActionTypes("LOADTAB", [
  "FAILURE",
  "GET_ONE",
  "SUCCESS"
])