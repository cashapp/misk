import { COLOR, COLORS } from '../constants'

/**
 * Group Action Union Definitions and resulting action functions by their namespace (ie. Counter)
 * and then in an identical order (ie. functions ordered same as actions within namespace)
*/

export type Action = { 
    type: COLOR.CHANGE, color: (COLORS.GREEN | COLORS.RED) }

export const changeColor = (newColor: (COLORS.GREEN | COLORS.RED)): Action => ({type: COLOR.CHANGE, color: newColor})
