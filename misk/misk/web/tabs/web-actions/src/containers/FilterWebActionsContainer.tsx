import {
  ControlGroup,
  HTMLSelect,
  InputGroup,
  Spinner
} from "@blueprintjs/core"
import { onChangeFnCall } from "@misk/simpleredux"
import * as React from "react"
import { connect } from "react-redux"
import {
  IDispatchProps,
  mapDispatchToProps,
  mapStateToProps,
  WebActionInternalLabel
} from "../ducks"

const FilterWebActionsContainer = (
  props: { disabled?: boolean; tag: string } & IDispatchProps
) => {
  const FilterSelectOptions = [...Object.keys(WebActionInternalLabel)]
  const filterTag = `${props.tag}::Filter`
  return (
    <ControlGroup fill={true}>
      <HTMLSelect
        disabled={props.disabled}
        large={true}
        onChange={onChangeFnCall(
          props.simpleMergeData,
          `${filterTag}::HTMLSelect`
        )}
        options={FilterSelectOptions}
      />
      <InputGroup
        disabled={props.disabled}
        large={true}
        onChange={onChangeFnCall(props.simpleMergeData, `${filterTag}::Input`)}
        placeholder={
          props.disabled ? "Loading Web Actions..." : "Filter Web Actions"
        }
        rightElement={props.disabled ? <Spinner size={20} /> : <span />}
      />
      {/* TODO(adrw) Use multiselect to do autocomplete filters */}
    </ControlGroup>
  )
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(FilterWebActionsContainer)
