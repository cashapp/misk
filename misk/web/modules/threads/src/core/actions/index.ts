import { COUNTER } from '../constants'

/**
 * Group Action Union Definitions and resulting action functions by their namespace (ie. Counter)
 * and then in an identical order (ie. functions ordered same as actions within namespace)
*/

export type Action = { 
    type: COUNTER.INCREMENT, } | {
    type: COUNTER.DECREMENT, }

export const incrementCounter = (): Action => ({type: COUNTER.INCREMENT})
export const decrementCounter = (): Action => ({type: COUNTER.DECREMENT})
