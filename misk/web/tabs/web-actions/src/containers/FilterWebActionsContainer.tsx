import { ControlGroup, HTMLSelect, InputGroup } from "@blueprintjs/core"
import { onChangeFnCall } from "@misk/simpleredux"
import * as React from "react"
import { connect } from "react-redux"
import {
  IDispatchProps,
  mapDispatchToProps,
  mapStateToProps,
  WebActionInternalLabel
} from "../ducks"

const FilterWebActionsContainer = (props: { tag: string } & IDispatchProps) => {
  const FilterSelectOptions = [...Object.keys(WebActionInternalLabel)]
  const filterTag = `${props.tag}::Filter`
  return (
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
        onChange={onChangeFnCall(props.simpleFormInput, `${filterTag}::Input`)}
        placeholder={"Filter Web Actions"}
      />
      {/* TODO(adrw) Use multiselect to do autocomplete filters */}
    </ControlGroup>
  )
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(FilterWebActionsContainer)
