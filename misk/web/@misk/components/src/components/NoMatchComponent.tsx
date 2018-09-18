import * as React from "react"

export interface INoMatchProps {
  prefix: string
}

export const NoMatchComponent = (props: INoMatchProps) => (
  <div>
    {props.prefix}: No Match Found
  </div>
)
