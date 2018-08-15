import * as React from "React"

export interface INoMatchProps {
  prefix: string
}

const NoMatchComponent = (props: INoMatchProps) => (
  <div>
    {props.prefix}: No Match Found
  </div>
)

export { NoMatchComponent }
