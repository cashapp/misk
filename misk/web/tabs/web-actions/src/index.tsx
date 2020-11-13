import { createApp, createIndex } from "@misk/core"
import * as Ducks from "./ducks"
import routes from "./routes"
export * from "./containers"

createIndex("web-actions", createApp(routes), Ducks)
