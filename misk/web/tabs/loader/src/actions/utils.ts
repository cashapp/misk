import { ITypes } from "./types";

export interface IError {
  errorCode: string
  response: {
    status: number
  }
}

export function createActionTypes (base: string, actions: string[] = []): ITypes {
  return actions.reduce((acc: ITypes, type: string) => {
    acc[type] = `${base}_${type}`
    return acc
  }, {})
}

export interface IAction<T, P> {
  readonly type: T
  readonly payload?: P
}

export function createAction<T extends string, P>(type: T, payload: P): IAction<T,P> {
  return {type, payload}
}

export const errorMessage = (error: IError) => {
  if (!error) {
    return ""
  }

  let code = error.errorCode
  if (!code) {
    code = error.response && error.response.status === 401
      ? 'Unauthorized'
      : 'InternalServerError'
  }

  return code
}
