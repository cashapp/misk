export interface IMultibinder {
  [key:string]: any[]
}

export enum binders {
  TabEntry = "TabEntry"
}

export const multibind = (binder: binders, name: string, Component: any) => {
  const Window = (window as any)
  console.log("register call:", name)
  console.log(Component)
  Window.MiskBinders = Window.MiskBinders || {}
  Window.MiskBinders[binder] = Window.MiskBinders[binder] || []
  if (name in Window.MiskBinders[binder]) {
    console.log("tab registered already")
  } else {
    Window.MiskBinders[binder].push({name, Component})
  }
}