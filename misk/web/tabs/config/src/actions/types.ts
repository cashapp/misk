import { createActionTypes } from "./utils"

export interface IActionType {
  [base:string]: string
}

export const CONFIG = createActionTypes("CONFIG", [
  "FAILURE",
  "GET_All",
  "SUCCESS"
])