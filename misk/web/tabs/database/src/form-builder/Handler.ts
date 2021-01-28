import get from "lodash/get"

/**
 * Handler functions to make provide event handlers for components in props
 *
 * <InputGroup onChange={handler.simpleMergeData("my-tag", options)} />
 *
 * Note:
 * - Options and ...options.overrideArgs values are optional
 * - Data will get implicitly passed in by extracting out parseEventInput(...onChangeArgs) from
 *   the component
 */

/**
 * IHandlerOptions extends IDispatchOptions by adding an overrideArgs field that,
 * if present, is used instead of the onChange event provided args
 */
export interface IHandlerOptions {
  overrideArgs?: any
}

/** Don't persist raw React events thinking they are deliberate objects */
export const isSyntheticEvent = (obj: any): boolean => {
  if (
    typeof obj === "object" &&
    "nativeEvent" in obj &&
    "currentTarget" in obj &&
    "target" in obj &&
    "bubbles" in obj &&
    "cancelable" in obj &&
    "defaultPrevented" in obj &&
    "eventPhase" in obj &&
    "isTrusted" in obj &&
    "preventDefault" in obj &&
    "isDefaultPrevented" in obj &&
    "stopPropagation" in obj &&
    "isPropagationStopped" in obj &&
    "persist" in obj &&
    "timeStamp" in obj &&
    "type" in obj
  ) {
    return true
  } else {
    return false
  }
}

/**
 * Provides normalized handling of component onChange varied inputs
 * @param args array of any event input from a component onChange function
 */
export const parseOnChangeArgs = (args: any) => {
  const debugLogs = false
  debugLogs && console.log("PARSING ARGS YOO", args)
  if (args[0] && typeof args[0] === "object" && "target" in args[0]) {
    // onChange=(event: [{ target: { value: any } }] ) => ... }
    debugLogs && console.log("PARSED: arg[] target val", args)
    return args[0].target.value
  } else if (args && args.target && args.target.value) {
    // onChange={(value: number) => ... }
    debugLogs && console.log("PARSED: arg target val", args)
    return args.target.value
  } else if (
    args.length === 2 &&
    typeof args[0] === "number" &&
    typeof args[1] === "string"
  ) {
    // onChange={(valueAsNumber: number, valueAsString: string) => ... }
    debugLogs && console.log("PARSED: NumberInput", args)
    return args[1]
  } else if (
    args.length === 2 &&
    args[0] instanceof Date &&
    typeof args[1] === "boolean"
  ) {
    // onChange={(selectedDate: Date, isUserChange: boolean) => ... }
    // We just want the Date, we don't really care how we got it.
    debugLogs && console.log("PARSED: date", args)
    return args[0]
  } else if (args.length === 1 && !isSyntheticEvent(args[0])) {
    // overrideArgs
    debugLogs && console.log("PARSED: overrideArgs", args)
    return args[0]
  } else if (args.length > 1) {
    // args are an array
    debugLogs && console.log("PARSED: args are an array", args)
    return args
  } else if (
    typeof args === "object" ||
    typeof args === "number" ||
    typeof args === "string"
  ) {
    // args are an object, number, or string
    debugLogs &&
      console.log("PARSED: args are an object, number, or string", args)
    return args
  } else {
    debugLogs && console.log("PARSED: null", args)
    return null
  }
}

export interface IHandler {
  /**
   * Handle onClick or onChange event to pass along extracted values to a delegateFn
   * @param options? if overrideArgs key set, delegateFn is called with the
   *    overrideArgs not the extracted value
   */
  handle: (
    delegateFn: (...onChangeArgs: any) => any,
    options?: IHandlerOptions
  ) => (...onChangeArgs: any) => void
}

export const handler: IHandler = {
  handle: (
    delegateFn: (...onChangeArgs: any) => any,
    options?: IHandlerOptions
  ) => (...onChangeArgs: any) =>
    delegateFn(parseOnChangeArgs(get(options, "overrideArgs", onChangeArgs))),
}
