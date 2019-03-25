import { ControlGroup, HTMLSelect, InputGroup } from "@blueprintjs/core"
import { onChangeFnCall } from "@misk/simpleredux"
import * as React from "react"
import { IDispatchProps, IState, WebActionInternalLabel } from "../ducks"

export const FilterWebActionsComponent = (
  props: IState & IDispatchProps & { tag: string }
) => {
  const FilterSelectOptions = [...Object.keys(WebActionInternalLabel)]
  const filterTag = `${props.tag}::Filter`
  return (
    <div>
      <ControlGroup fill={true}>
        <HTMLSelect
          large={true}
          onChange={onChangeFnCall(
            props.simpleFormInput,
            `${filterTag}::HTMLSelect`
          )}
          options={FilterSelectOptions}
        />
        <InputGroup
          large={true}
          onChange={onChangeFnCall(
            props.simpleFormInput,
            `${filterTag}::Input`
          )}
          placeholder={"Filter Web Actions"}
        />
        {/* TODO(adrw) Use multiselect to do autocomplete filters */}
      </ControlGroup>
    </div>
  )
}
