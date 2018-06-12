import { IconName as BlueprintjsIconName } from '@blueprintjs/icons'

export type menuItem = {
    text: string,
    icon: BlueprintjsIconName,
    className: string,
    url: string,
    activeOnlyWhenExact: boolean
}

// default icon: applications

const menu =  [
    {
    text: "Home",
    icon: "home",
    url: "/",
},  {
    text: "Config",
    icon: "properties",
    url: "/config",
},  {
    text: "Log4j",
    icon: "annotation",
    url: "/log4j",
},  {
    text: "Threads",
    icon: "comparison",
    url: "/threads"
}
]

export default menu