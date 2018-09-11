import { IMiskBinderKeys } from "@misk/common"

export const multibind = (binderKey: IMiskBinderKeys, key: string, value: any) => {
  const Window = (window as any)
  Window.MiskBinders = Window.MiskBinders || {}
  Window.MiskBinders[binderKey] = Window.MiskBinders[binderKey] || []
  if (key in Window.MiskBinders[binderKey]) {
    console.warn("key registered already")
  } else {
    Window.MiskBinders[binderKey].push({key, value})
  }
  return Window.MiskBinders[binderKey]
}
