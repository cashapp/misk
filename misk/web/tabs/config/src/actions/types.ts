import { createActionTypes } from "./utils"

export interface IActionType {
  [base:string]: string
}

// export const CONFIG = createActionTypes("CONFIG", [
//   "FAILURE",
//   "GET_All",
//   "SUCCESS"
// ])

export enum CONFIG {
  FAILURE = "CONFIG_FAILURE",
  GET_ALL = "CONFIG_GET_ALL",
  SUCCESS = "CONFIG_SUCCESS"
}