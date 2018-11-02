import { Card, Classes, NonIdealState } from "@blueprintjs/core"
import { IconName, IconNames } from "@blueprintjs/icons"
import * as React from "react"
import { ErrorCalloutComponent, IError } from "./ErrorCalloutComponent"

/**
 * <OfflineComponent
 *    error={props.error}
 *    icon={IconNames.OFFLINE}
 *    title={"Uh oh!"}
 *    description={"We broke it."}
 *    endpoint={"/api/broken/endpoint"}
 * />
 */

export interface IOfflineProps {
  icon?: IconName
  title?: string
  description?: string
  endpoint?: string
  error?: IError
}

const generateDescription = (props: IOfflineProps) => {
  const description = props.description ? `${props.description}\n` : ""
  const endpoint = props.endpoint
    ? `Error trying to reach: ${props.endpoint}.\n`
    : ""
  return `${description}${endpoint}`
}

export const OfflineComponent = (props: IOfflineProps) => (
  <div>
    <NonIdealState
      icon={props.icon ? props.icon : IconNames.OFFLINE}
      title={props.title ? props.title : "Loading Error"}
      description={generateDescription(props)}
    >
      <Card>
        <h5 className={Classes.SKELETON}>Your head is not an artifact!</h5>
        <p className={Classes.SKELETON}>
          Maybe we better talk out here; the observation lounge has turned into
          a swamp. Some days you get the bear, and some days the bear gets you.
        </p>
      </Card>
    </NonIdealState>
    {props.error ? <ErrorCalloutComponent error={props.error} /> : <span />}
  </div>
)
