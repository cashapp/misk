import {
  ButtonGroup,
  Card,
  ControlGroup,
  FormGroup,
  InputGroup,
  Intent,
  Label
} from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { WrapTextContainer } from "@misk/core"
import {
  onChangeFnCall,
  onChangeToggleFnCall,
  onClickFnCall,
  simpleSelect,
  simpleType
} from "@misk/simpleredux"
import { OrderedMap } from "immutable"
import * as React from "react"
import { connect } from "react-redux"
import styled from "styled-components"
import {
  Button,
  Metadata,
  QuantityButton,
  Tooltip,
  WrapTextArea
} from "../components"
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

const Field = styled(FormGroup)`
  margin: 0px !important;
`

const repeatableFieldButtons = (
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
  const editRawButton = (
    <Tooltip content={"Edit raw input"} key={`editRaw${id}`} lazy={true}>
      <Button
        active={simpleSelect(
          props.simpleForm,
          `${tag}::EditRawButton:${padId(id)}`,
          "data",
          simpleType.boolean
        )}
        icon={IconNames.MORE}
        onClick={onChangeToggleFnCall(
          props.simpleFormToggle,
          `${tag}::EditRawButton:${padId(id)}`,
          props.simpleForm
        )}
      />
    </Tooltip>
  )
  const dirtyInput = metadata.dirtyInput
  const dirtyInputButton = (
    <Tooltip
      content={dirtyInput ? "Remove from request body" : "Add to request body"}
      key={`dirtyInput${id}`}
      lazy={true}
    >
      <Button
        icon={dirtyInput ? IconNames.REMOVE : IconNames.ADD_TO_ARTIFACT}
        onClick={(event: any) => {
          props.simpleFormInput(`${tag}::ButtonRequestBody`, true)
          ;(dirtyInput
            ? props.webActionsUnsetDirtyInput
            : props.webActionsSetDirtyInput)(id, action, props.webActionsRaw)
        }}
      />
    </Tooltip>
  )
  if (
    metadata &&
    metadata.id !== "0" &&
    typesMetadata.get(metadata.idParent).repeated
  ) {
    const { idParent } = metadata
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
    const removeButton = idChildren.size > 1 && (
      <QuantityButton
        action={action}
        changeFn={props.webActionsRemove}
        content={"Remove Field"}
        icon={IconNames.CROSS}
        id={id}
        key={`Remove${id}`}
        oldState={props.webActionsRaw}
      />
    )
    return [dirtyInputButton, editRawButton, addButton, removeButton]
  } else if (metadata && !metadata.repeated && metadata.typescriptType) {
    return [dirtyInputButton, editRawButton]
  } else if (metadata && id !== "0") {
    return [dirtyInputButton]
  } else if (metadata && metadata.id === "0") {
    return []
  } else {
    return [<Button icon={IconNames.WARNING_SIGN} />]
  }
}

const EditRawInput = (
  props: { children: any; id: string; tag: string } & IDispatchProps & IState
) => {
  const { children, id, tag } = props
  const editRawIsOpen = simpleSelect(
    props.simpleForm,
    `${tag}::EditRawButton:${padId(id)}`,
    "data",
    simpleType.boolean
  )
  if (editRawIsOpen) {
    return (
      <WrapTextArea
        fill={true}
        growVertically={true}
        onChange={onChangeFnCall(props.simpleFormInput, `${tag}::${padId(id)}`)}
        value={simpleSelect(props.simpleForm, `${tag}::${padId(id)}`, "data")}
      />
    )
  } else {
    return children
  }
}

const formLabelFormatter = (name: string, serverType: string) => {
  if (name && serverType) {
    const st = serverType.split(".").join(" ")
    return <WrapTextContainer>{`${name} (${st})`}</WrapTextContainer>
  } else {
    return <span />
  }
}

const clickDirtyInputFns = (
  props: {
    action: IWebActionInternal
    id: string
    tag: string
    typesMetadata: OrderedMap<string, ITypesFieldMetadata>
  } & IState &
    IDispatchProps
) => () => {
  const { action, id, tag } = props
  props.webActionsSetDirtyInput(id, action, props.webActionsRaw)
  props.simpleFormInput(`${tag}::ButtonRequestBody`, true)
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
      name,
      idChildren,
      serverType,
      typescriptType
    } = metadata as ITypesFieldMetadata
    if (typescriptType === TypescriptBaseTypes.boolean) {
      return (
        <Field label={formLabelFormatter(name, serverType)}>
          <ControlGroup>
            {...repeatableFieldButtons({ ...props, id })}
            <EditRawInput {...props} id={id} tag={tag}>
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
                onClick={(event: any) => {
                  props.simpleFormToggle(
                    `${tag}::${padId(id)}`,
                    props.simpleForm
                  )
                  clickDirtyInputFns(props)()
                }}
              >
                {simpleSelect(
                  props.simpleForm,
                  `${tag}::${padId(id)}`,
                  "data"
                ).toString() || "unset"}
              </Button>
            </EditRawInput>
          </ControlGroup>
        </Field>
      )
    } else if (typescriptType === TypescriptBaseTypes.number) {
      return (
        <Field label={formLabelFormatter(name, serverType)}>
          <ControlGroup>
            {...repeatableFieldButtons({ ...props, id })}
            <EditRawInput {...props} id={id} tag={tag}>
              <InputGroup
                onClick={onChangeFnCall(clickDirtyInputFns(props))}
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
            </EditRawInput>
          </ControlGroup>
        </Field>
      )
    } else if (typescriptType === TypescriptBaseTypes.string) {
      return (
        <Field label={formLabelFormatter(name, serverType)}>
          <ControlGroup>
            {...repeatableFieldButtons({ ...props, id })}
            <EditRawInput {...props} id={id} tag={tag}>
              <InputGroup
                onClick={onChangeFnCall(clickDirtyInputFns(props))}
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
            </EditRawInput>
          </ControlGroup>
        </Field>
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
        const fieldGroup = (child: string, parentServerType: string) => {
          const {
            idChildren: grandChildren,
            name: childName,
            serverType: childServerType
          } = typesMetadata.get(child)
          const parentOfLeaf =
            grandChildren.reduce(
              (sum: number, grandchild: string) =>
                typesMetadata.get(grandchild).idChildren.size + sum,
              0
            ) === 0
          const noParentGroupLabel = parentOfLeaf && parentServerType === null
          if (
            BaseFieldTypes.hasOwnProperty(childServerType) ||
            noParentGroupLabel
          ) {
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
                <Label>{formLabelFormatter(childName, childServerType)}</Label>
                <ButtonGroup>
                  {...repeatableFieldButtons({ ...props, id: child })}
                </ButtonGroup>
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
        if (id === "0") {
          const whichFormData = simpleSelect(
            props.simpleForm,
            `${tag}::RequestBodyFormInputType`,
            "data",
            simpleType.boolean
          )
            ? "RAW"
            : "FORM"
          return (
            <div>
              <ControlGroup fill={true}>
                <Button
                  active={whichFormData === "FORM"}
                  icon={IconNames.FORM}
                  onClick={onChangeToggleFnCall(
                    props.simpleFormToggle,
                    `${tag}::RequestBodyFormInputType`,
                    props.simpleForm
                  )}
                  text={"Form"}
                />
                <Button
                  active={whichFormData === "RAW"}
                  icon={IconNames.MORE}
                  onClick={onChangeToggleFnCall(
                    props.simpleFormToggle,
                    `${tag}::RequestBodyFormInputType`,
                    props.simpleForm
                  )}
                  text={"Raw"}
                />
              </ControlGroup>
              {whichFormData === "FORM" ? (
                <RequestFieldGroup>
                  {idChildren.map((child: string) =>
                    fieldGroup(child, serverType)
                  )}
                </RequestFieldGroup>
              ) : (
                <RequestFieldGroup>
                  <WrapTextArea
                    fill={true}
                    growVertically={true}
                    onClick={onClickFnCall(clickDirtyInputFns(props))}
                    onChange={onChangeFnCall(
                      props.simpleFormInput,
                      `${tag}::RawRequestBody`
                    )}
                    placeholder={
                      "Raw request body. This input will return a string or JSON."
                    }
                  />
                </RequestFieldGroup>
              )}
            </div>
          )
        } else {
          return (
            <RequestFieldGroup>
              {idChildren.map((child: string) => fieldGroup(child, serverType))}
            </RequestFieldGroup>
          )
        }
      }
    } else {
      return (
        <Field label={formLabelFormatter(name, serverType)}>
          <ControlGroup>
            {...repeatableFieldButtons({ ...props, id })}
            <WrapTextArea
              fill={true}
              growVertically={true}
              onClick={onClickFnCall(clickDirtyInputFns(props))}
              onChange={onChangeFnCall(
                props.simpleFormInput,
                `${tag}::${padId(id)}`
              )}
              placeholder={
                "Unparseable type. This input will return a string or JSON."
              }
            />
          </ControlGroup>
        </Field>
      )
    }
  } else {
    return (
      <Metadata
        content={`No Request Body for ${action.name} ${action.pathPattern}`}
      />
    )
  }
}

const RequestFormFieldBuilderContainer = connect(
  mapStateToProps,
  mapDispatchToProps
)(UnconnectedRequestFormFieldBuilderContainer)

export default RequestFormFieldBuilderContainer
