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
import { connect } from "react-redux"
import styled from "styled-components"
import { QuantityButton } from "../components"
import {
  BaseFieldTypes,
  IDispatchProps,
  IState,
  ITypesFieldMetadata,
  IWebActionInternal,
  mapDispatchToProps,
  mapStateToProps,
  padId,
  TypescriptBaseTypes
} from "../ducks"

const RequestFieldGroup = styled(Card)`
  box-shadow: none !important;
  border-left: 2px black;
  margin-bottom: 0px;
  padding: 0px 0px 0px 15px !important;
`

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
    const addButton = (
      <QuantityButton
        action={action}
        changeFn={props.webActionsAdd}
        content={"Add Field"}
        icon={IconNames.PLUS}
        id={idParent}
        key={`Add${id}`}
        oldState={props.webActionsRaw}
      />
    )
    const removeButton =
      idChildren.size > 1 ? (
        <QuantityButton
          action={action}
          changeFn={props.webActionsRemove}
          content={"Remove Field"}
          icon={IconNames.CROSS}
          id={id}
          key={`Remove${id}`}
          oldState={props.webActionsRaw}
        />
      ) : (
        <span key={`span${id}`} />
      )
    return [
      <Tooltip content={`Repeated ${serverType}`} key={`repeated${id}`}>
        <Button icon={IconNames.REPEAT}>{name}</Button>
      </Tooltip>,
      addButton,
      removeButton
    ]
  } else if (metadata && !metadata.repeated) {
    const { name, serverType } = metadata
    return [
      <Tooltip content={serverType} key={`notrepeated${id}`}>
        <Button>{name || "Body"}</Button>
      </Tooltip>
    ]
  } else {
    return [<span key={`span${id}`} />]
  }
}

const UnconnectedRequestFormFieldBuilderContainer = (
  props: {
    action: IWebActionInternal
    id: string
    tag: string
    typesMetadata: OrderedMap<string, ITypesFieldMetadata>
  } & IState &
    IDispatchProps
) => {
  const { action, id, tag, typesMetadata } = props
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
          <Tooltip
            content={"Toggle large text input"}
            key={`toggleLargeText${id}`}
          >
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
              <RequestFormFieldBuilderContainer
                action={action}
                id={child}
                key={child}
                tag={tag}
                typesMetadata={typesMetadata}
              />
            ))}
          </div>
        )
      } else {
        const fieldGroup = (child: string) => {
          const { serverType: childServerType } = typesMetadata.get(child)
          if (BaseFieldTypes.hasOwnProperty(childServerType)) {
            return (
              <div key={child}>
                <RequestFormFieldBuilderContainer
                  action={action}
                  id={child}
                  tag={tag}
                  typesMetadata={typesMetadata}
                />
              </div>
            )
          } else {
            return (
              <div key={child}>
                <ControlGroup>
                  {...repeatableFieldButtons({ ...props, id: child })}
                </ControlGroup>
                <RequestFormFieldBuilderContainer
                  action={action}
                  id={child}
                  tag={tag}
                  typesMetadata={typesMetadata}
                />
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

const RequestFormFieldBuilderContainer = connect(
  mapStateToProps,
  mapDispatchToProps
)(UnconnectedRequestFormFieldBuilderContainer)

export default RequestFormFieldBuilderContainer
