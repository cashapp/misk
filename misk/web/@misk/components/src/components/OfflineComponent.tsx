import { Card, Classes, NonIdealState } from "@blueprintjs/core"
import { IconName, IconNames } from "@blueprintjs/icons"
import * as React from "react"

export interface IOfflineProps {
  icon?: IconName
  title?: string
  description?: string
  endpoint?: string
}

export const OfflineComponent = (props: IOfflineProps) => (
  <NonIdealState 
    icon={props.icon ? props.icon : IconNames.OFFLINE}
    title={props.title ? props.title : "Loading Error"}
    description={props.endpoint ? `The following server endpoint(s) are unavailable: ${props.endpoint}.` : props.description ? props.description : `A server endpoint is unavailable.`}
  >
    <Card>
      <h5 className={Classes.SKELETON}>Your head is not an artifact!</h5>
      <p className={Classes.SKELETON}>Maybe we better talk out here; 
      the observation lounge has turned into a swamp. 
      Some days you get the bear, and some days the bear gets you.</p>
    </Card>
  </NonIdealState>
)