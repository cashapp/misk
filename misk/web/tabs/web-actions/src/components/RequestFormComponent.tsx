import {
  Button,
  Card,
  ControlGroup,
  InputGroup,
  Intent,
  TextArea,
  Tooltip
} from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import {
  onChangeFnCall,
  onChangeToggleFnCall,
  simpleSelect,
  simpleType
} from "@misk/simpleredux"
import { OrderedMap } from "immutable"
import * as React from "react"
import styled from "styled-components"
import {
  BaseFieldTypes,
  findIndexAction,
  IDispatchProps,
  IState,
  ITypesFieldMetadata,
  IWebActionInternal,
  padId,
  TypescriptBaseTypes
} from "../ducks"

const RequestFieldGroup = styled(Card)`
  box-shadow: none !important;
  border-left: 2px black;
  margin-bottom: 0px;
  padding: 0px 0px 0px 15px !important;
`

const AddButton = (
  props: {
    idParent: string
    action: IWebActionInternal
  } & IState &
    IDispatchProps
) => (
  <Tooltip content={"Add Field"}>
    <Button
      icon={IconNames.PLUS}
      onClick={onChangeFnCall(
        props.webActionsAdd,
        props.idParent,
        props.action,
        props.webActionsRaw
      )}
    />
  </Tooltip>
)

const RemoveButton = (
  props: {
    id: string
    action: IWebActionInternal
  } & IState &
    IDispatchProps
) => (
  <Tooltip content={"Remove Field"}>
    <Button
      icon={IconNames.CROSS}
      onClick={onChangeFnCall(
        props.webActionsRemove,
        props.id,
        props.action,
        props.webActionsRaw
      )}
    />
  </Tooltip>
)

const repeatableFieldButtons = (
  props: {
    action: IWebActionInternal
    id: string
    typesMetadata: OrderedMap<string, ITypesFieldMetadata>
  } & IState &
    IDispatchProps
) => {
  const { action, id, typesMetadata } = props
  const metadata = typesMetadata.get(id)
  if (
    metadata &&
    metadata.id !== "0" &&
    typesMetadata.get(metadata.idParent).repeated
  ) {
    const { idParent, name, serverType } = metadata
    const { idChildren } = typesMetadata.get(idParent)
    const removeButton =
      idChildren.size > 1 ? (
        <RemoveButton {...props} action={action} id={id} />
      ) : (
        <span />
      )
    return [
      <Tooltip content={`Repeated ${serverType}`}>
        <Button icon={IconNames.REPEAT}>{name}</Button>
      </Tooltip>,
      <AddButton {...props} action={action} idParent={idParent} />,
      removeButton
    ]
  } else if (metadata && !metadata.repeated) {
    const { name, serverType } = metadata
    return [
      <Tooltip content={serverType}>
        <Button>{name || "Body"}</Button>
      </Tooltip>
    ]
  } else {
    return [<span />]
  }
}

const RequestFormFieldBuilder = (
  props: {
    action: IWebActionInternal
    id: string
    tag: string
    typesMetadata: OrderedMap<string, ITypesFieldMetadata>
  } & IState &
    IDispatchProps
) => {
  const { id, tag, typesMetadata } = props
  const metadata = typesMetadata.get(id)
  if (metadata) {
    const {
      idChildren,
      serverType,
      typescriptType
    } = metadata as ITypesFieldMetadata
    if (typescriptType === TypescriptBaseTypes.boolean) {
      return (
        <ControlGroup>
          {...repeatableFieldButtons({ ...props, id })}
          <Button
            intent={
              simpleSelect(
                props.simpleForm,
                `${tag}::${padId(id)}`,
                "data",
                simpleType.boolean
              )
                ? Intent.PRIMARY
                : Intent.WARNING
            }
            onClick={onChangeToggleFnCall(
              props.simpleFormToggle,
              `${tag}::${padId(id)}`,
              props.simpleForm
            )}
          >
            {simpleSelect(
              props.simpleForm,
              `${tag}::${padId(id)}`,
              "data",
              simpleType.boolean
            ).toString()}
          </Button>
        </ControlGroup>
      )
    } else if (typescriptType === TypescriptBaseTypes.number) {
      return (
        <ControlGroup>
          {...repeatableFieldButtons({ ...props, id })}
          <InputGroup
            onChange={onChangeFnCall(
              props.simpleFormInput,
              `${tag}::${padId(id)}`
            )}
            placeholder={serverType}
            value={simpleSelect(
              props.simpleForm,
              `${tag}::${padId(id)}`,
              "data"
            )}
          />
        </ControlGroup>
      )
    } else if (typescriptType === TypescriptBaseTypes.string) {
      return (
        <ControlGroup>
          {...repeatableFieldButtons({ ...props, id })}
          <Tooltip content={"Toggle large text input"}>
            <Button
              active={simpleSelect(
                props.simpleForm,
                `${tag}::LongText:${padId(id)}`,
                "data",
                simpleType.boolean
              )}
              icon={IconNames.MORE}
              onClick={onChangeToggleFnCall(
                props.simpleFormToggle,
                `${tag}::LongText:${padId(id)}`,
                props.simpleForm
              )}
            />
          </Tooltip>
          {simpleSelect(
            props.simpleForm,
            `${tag}::LongText:${padId(id)}`,
            "data",
            simpleType.boolean
          ) ? (
            <TextArea
              fill={true}
              onChange={onChangeFnCall(
                props.simpleFormInput,
                `${tag}::${padId(id)}`
              )}
              placeholder={`${serverType}\nDrag bottom right corner of text area input to expand.`}
              value={simpleSelect(
                props.simpleForm,
                `${tag}::${padId(id)}`,
                "data"
              )}
            />
          ) : (
            <InputGroup
              onChange={onChangeFnCall(
                props.simpleFormInput,
                `${tag}::${padId(id)}`
              )}
              placeholder={serverType}
              value={simpleSelect(
                props.simpleForm,
                `${tag}::${padId(id)}`,
                "data"
              )}
            />
          )}
        </ControlGroup>
      )
    } else if (typescriptType === null && idChildren.size > 0) {
      if (
        idChildren.first() &&
        typesMetadata.get(idChildren.first()).typescriptType &&
        BaseFieldTypes.hasOwnProperty(serverType)
      ) {
        return (
          <div>
            {idChildren.map((child: string) => (
              <RequestFormFieldBuilder {...props} id={child} />
            ))}
          </div>
        )
      } else {
        const fieldGroup = (child: string) => {
          const { serverType: childServerType } = typesMetadata.get(child)
          if (BaseFieldTypes.hasOwnProperty(childServerType)) {
            return (
              <div>
                <RequestFormFieldBuilder {...props} id={child} />
              </div>
            )
          } else {
            return (
              <div>
                <ControlGroup>
                  {...repeatableFieldButtons({ ...props, id: child })}
                </ControlGroup>
                <RequestFormFieldBuilder {...props} id={child} />
              </div>
            )
          }
        }
        return (
          <RequestFieldGroup>
            {idChildren.map((child: string) => fieldGroup(child))}
          </RequestFieldGroup>
        )
      }
    } else {
      return (
        <ControlGroup>
          {...repeatableFieldButtons({ ...props, id })}
          <TextArea
            fill={true}
            onChange={onChangeFnCall(
              props.simpleFormInput,
              `${tag}::${padId(id)}`
            )}
            placeholder={
              "Unparseable type. (JSON or Text).\nDrag bottom right corner of text area input to expand."
            }
          />
        </ControlGroup>
      )
    }
  } else {
    return <span />
  }
}

export const RequestFormComponent = (
  props: {
    action: IWebActionInternal
    tag: string
  } & IState &
    IDispatchProps
) => {
  const { action } = props
  const { typesMetadata } = props.webActionsRaw.get("metadata")[
    findIndexAction(action, props.webActionsRaw)
  ]
  return (
    <RequestFormFieldBuilder
      {...props}
      id={"0"}
      typesMetadata={typesMetadata}
    />
  )
}
