import {
  Button,
  Card,
  ControlGroup,
  FormGroup,
  InputGroup,
  Intent,
  TextArea,
  Tooltip
} from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { onChangeFnCall, simpleSelect, simpleType } from "@misk/simpleredux"
import { OrderedSet } from "immutable"
import uniqueId from "lodash/uniqueId"
import * as React from "react"
import styled from "styled-components"
import {
  BaseFieldTypes,
  IActionTypes,
  IDispatchProps,
  IFieldTypeMetadata,
  IState,
  IWebActionInternal,
  TypescriptBaseTypes
} from "../ducks"

interface IFieldProps {
  field: IFieldTypeMetadata
  id: number
  nestPath: string
  tag: string
  types: IActionTypes
}

const RequestFieldGroup = styled(Card)`
  margin-bottom: 10px;
`

const RequestFormGroup = styled(FormGroup)`
  margin: 0 !important;
`

const generateKeyTag = (props: IFieldProps) =>
  `${props.tag}::${props.nestPath}${props.field.name}::Keys`

const safeNumberArray = (ids: number | number[]) => {
  if (typeof ids === "number") {
    return [ids]
  } else {
    return ids
  }
}

const RepeatableFieldButton = (
  props: IFieldProps & IState & IDispatchProps
) => {
  const { repeated } = props.field
  const { id } = props
  const tag = generateKeyTag(props)
  const ids = safeNumberArray(
    simpleSelect(props.simpleForm, tag, "data", simpleType.array)
  )
  if (repeated) {
    return (
      <div>
        <Button
          icon={IconNames.PLUS}
          onClick={onChangeFnCall(
            props.simpleFormInput,
            tag,
            OrderedSet(ids)
              .add(parseInt(uniqueId()))
              .toJS()
          )}
        />
        {ids && ids.length > 1 ? (
          <Button
            icon={IconNames.MINUS}
            onClick={onChangeFnCall(
              props.simpleFormInput,
              tag,
              OrderedSet(ids)
                .delete(id)
                .toJS()
            )}
          />
        ) : (
          <span />
        )}
      </div>
    )
  } else {
    return <span />
  }
}

const RequestFormField = (props: IFieldProps & IState & IDispatchProps) => {
  const { field, id, nestPath, tag } = props
  const { name, type } = field
  if (BaseFieldTypes.hasOwnProperty(type)) {
    if (BaseFieldTypes[type] === TypescriptBaseTypes.number) {
      return (
        <ControlGroup>
          <Tooltip content={type}>
            <Button>{name}</Button>
          </Tooltip>
          <RepeatableFieldButton {...props} id={id} nestPath={nestPath} />
          <InputGroup
            onChange={onChangeFnCall(
              props.simpleFormInput,
              `${tag}::${nestPath}${id}::Data`
            )}
            placeholder={type}
            value={simpleSelect(
              props.simpleForm,
              `${tag}::${nestPath}${id}::Data`,
              "data"
            )}
          />
        </ControlGroup>
      )
    } else if (BaseFieldTypes[type] === TypescriptBaseTypes.string) {
      return (
        <ControlGroup>
          <Tooltip content={type}>
            <Button>{name}</Button>
          </Tooltip>
          <RepeatableFieldButton {...props} id={id} nestPath={nestPath} />
          <TextArea
            fill={true}
            onChange={onChangeFnCall(
              props.simpleFormInput,
              `${tag}::${nestPath}${id}::Data`
            )}
            placeholder={`${type}\nDrag bottom right corner of text area input to expand.`}
            value={simpleSelect(
              props.simpleForm,
              `${tag}::${nestPath}${id}::Data`,
              "data"
            )}
          />
        </ControlGroup>
      )
    } else {
      return (
        <span>
          Valid Base Field Type {type} has no handler for the corresponding
          Tyepscript Type {BaseFieldTypes[type]}
        </span>
      )
    }
  } else if (props.types.hasOwnProperty(type)) {
    return (
      <div>
        <RepeatableFieldButton id={id} nestPath={nestPath} {...props} />
        <RequestFieldGroup>
          <RequestFormGroup label={`${name} (${type})`}>
            {props.types[type].fields.map((field: IFieldTypeMetadata) => {
              return (
                <div>
                  <RequestFormFields
                    {...props}
                    field={field}
                    id={parseInt(`${id}${uniqueId()}`)}
                    nestPath={`${nestPath}${id}/`}
                    types={props.types}
                  />
                </div>
              )
            })}
          </RequestFormGroup>
        </RequestFieldGroup>
      </div>
    )
  } else {
    return (
      <div>
        <RepeatableFieldButton id={id} nestPath={nestPath} {...props} /> {name}:
        <RequestFormGroup label={`${name}:${JSON.stringify(type)}:${id}`}>
          <TextArea
            fill={true}
            onChange={onChangeFnCall(
              props.simpleFormInput,
              `${tag}::${nestPath}${id}::Data`
            )}
            placeholder={
              "Unparseable type. (JSON or Text).\nDrag bottom right corner of text area input to expand."
            }
          />
        </RequestFormGroup>
      </div>
    )
  }
}

const RequestFormFields = (props: IFieldProps & IState & IDispatchProps) => {
  const uid = props.id || parseInt(uniqueId())
  const keyTag = generateKeyTag(props)
  const ids = safeNumberArray(
    simpleSelect(props.simpleForm, keyTag, "data", simpleType.array) ||
      (props.simpleFormInput(keyTag, OrderedSet([uid]).toJS()) && [uid])
  )
  return (
    <div>
      {ids.map((id: number) => (
        <RequestFormField {...props} id={id} />
      ))}
    </div>
  )
}

export const RequestFormComponent = (
  props: { action: IWebActionInternal; tag: string } & IState & IDispatchProps
) => {
  const { requestType, types } = props.action
  const { fields } = types[requestType]
  return (
    <div>
      {fields.map((field: IFieldTypeMetadata) => (
        <RequestFormFields
          {...props}
          field={field}
          id={0}
          nestPath={"/"}
          types={types}
        />
      ))}
    </div>
  )
}
