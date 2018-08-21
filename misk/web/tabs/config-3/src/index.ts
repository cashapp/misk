export * from "./TabContainer"
import TabContainer from "./TabContainer"

while (!(window as any).Misk.Binder) {}
(window as any).Misk.Binder.multibind((window as any).Misk.Binder.binders.TabEntry, "Config", TabContainer)
