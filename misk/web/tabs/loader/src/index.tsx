import { createApp, createIndex } from "@misk/components"
import * as Ducks from "./ducks"
import routes from "./routes"
export * from "./containers"

createIndex("loader", createApp(routes), Ducks)

export { multibind } from "./utils/binder"
