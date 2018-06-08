import { IconName as BlueprintjsIconName } from '@blueprintjs/icons'

export type menuItem = {
    title: string,
    icon: BlueprintjsIconName,
    className: string,
    url: string
}

// default icon: applications

const menu =  [
    {
    title: "Home",
    icon: "home",
    url: "/",
},  {
    title: "Config",
    icon: "properties",
    url: "/config",
},  {
    title: "Log4j",
    icon: "annotation",
    url: "/log4j",
},  {
    title: "Threads",
    icon: "comparison",
    url: "/threads"
}
]

export default menu