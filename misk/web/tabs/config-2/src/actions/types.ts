export interface IActionType {
  CONFIG: CONFIG
}

export enum CONFIG {
  FAILURE = "CONFIG_FAILURE",
  GET_ALL = "CONFIG_GET_ALL",
  SUCCESS = "CONFIG_SUCCESS"
}