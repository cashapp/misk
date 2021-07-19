/** @jsx jsx */
import {
  Button,
  ButtonGroup,
  Card,
  ControlGroup,
  FormGroup,
  HTMLSelect,
  InputGroup,
  Intent,
  Label,
  TextArea,
  Tooltip
} from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { css, jsx } from "@emotion/core"
import { WrapTextContainer } from "@misk/core"
import {
  onChangeFnCall,
  onChangeToggleFnCall,
  onClickFnCall,
  simpleSelectorGet
} from "@misk/simpleredux"
import { OrderedMap } from "immutable"
import { connect } from "react-redux"
import {
  cssButton,
  cssTooltip,
  cssWrapTextArea,
  Metadata,
  QuantityButton
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
  parseEnumType,
  TypescriptBaseTypes,
  ServerTypes
} from "../ducks"

const cssCard = css`
  box-shadow: none !important;
  border-left: 2px black;
  margin-bottom: 0px;
  padding: 0px 0px 0px 15px !important;
`

const cssFormGroup = css`
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
    <Tooltip
      css={css(cssTooltip)}
      content={"Edit raw input"}
      key={`editRaw${id}`}
      lazy={true}
    >
      <Button
        active={simpleSelectorGet(
          props.simpleRedux,
          [`${tag}::EditRawButton:${padId(id)}`, "data"],
          false
        )}
        css={css(cssButton)}
        defaultValue={simpleSelectorGet(props.simpleRedux, [
          `${tag}::${padId(id)}`,
          "data"
        ])}
        icon={IconNames.MORE}
        onClick={onChangeToggleFnCall(
          props.simpleMergeToggle,
          `${tag}::EditRawButton:${padId(id)}`,
          props.simpleRedux
        )}
      />
    </Tooltip>
  )
  const dirtyInput = metadata.dirtyInput
  const dirtyInputButton = (
    <Tooltip
      css={css(cssTooltip)}
      content={dirtyInput ? "Remove from request body" : "Add to request body"}
      key={`dirtyInput${id}`}
      lazy={true}
    >
      <Button
        icon={dirtyInput ? IconNames.REMOVE : IconNames.ADD_TO_ARTIFACT}
        onClick={(event: any) => {
          props.simpleMergeData(`${tag}::ButtonRequestBody`, true)
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
    return [<Button css={css(cssButton)} icon={IconNames.WARNING_SIGN} />]
  }
}

const EditRawInput = (
  props: { children: any; id: string; tag: string } & IDispatchProps & IState
) => {
  const { children, id, tag } = props
  const editRawIsOpen = simpleSelectorGet(
    props.simpleRedux,
    [`${tag}::EditRawButton:${padId(id)}`, "data"],
    false
  )
  if (editRawIsOpen) {
    return (
      <TextArea
        css={css(cssWrapTextArea)}
        defaultValue={simpleSelectorGet(props.simpleRedux, [
          `${tag}::${padId(id)}`,
          "data"
        ])}
        fill={true}
        growVertically={true}
        onBlur={onChangeFnCall(props.simpleMergeData, `${tag}::${padId(id)}`)}
      />
    )
  } else {
    return children
  }
}

const formLabelFormatter = (name: string, serverType: string) => {
  if (name && serverType && serverType.startsWith(ServerTypes.Enum)) {
    const { enumClassName } = parseEnumType(serverType)
    return <WrapTextContainer>{`${name} (${enumClassName})`}</WrapTextContainer>
  } else if (name && serverType) {
    return <WrapTextContainer>{`${name} (${serverType})`}</WrapTextContainer>
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
  props.simpleMergeData(`${tag}::ButtonRequestBody`, true)
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
        <FormGroup
          css={css(cssFormGroup)}
          label={formLabelFormatter(name, serverType)}
        >
          <ControlGroup>
            {...repeatableFieldButtons({ ...props, id })}
            <EditRawInput {...props} id={id} tag={tag}>
              <Button
                css={css(cssButton)}
                defaultValue={simpleSelectorGet(props.simpleRedux, [
                  `${tag}::${padId(id)}`,
                  "data"
                ])}
                intent={
                  simpleSelectorGet(
                    props.simpleRedux,
                    [`${tag}::${padId(id)}`, "data"],
                    false
                  )
                    ? Intent.PRIMARY
                    : Intent.WARNING
                }
                onChange={onChangeFnCall(clickDirtyInputFns(props))}
                onClick={() => {
                  props.simpleMergeToggle(
                    `${tag}::${padId(id)}`,
                    props.simpleRedux
                  )
                  clickDirtyInputFns(props)()
                }}
              >
                {simpleSelectorGet(props.simpleRedux, [
                  `${tag}::${padId(id)}`,
                  "data"
                ]).toString() || "unset"}
              </Button>
            </EditRawInput>
          </ControlGroup>
        </FormGroup>
      )
    } else if (typescriptType === TypescriptBaseTypes.number) {
      return (
        <FormGroup
          css={css(cssFormGroup)}
          label={formLabelFormatter(name, serverType)}
        >
          <ControlGroup>
            {...repeatableFieldButtons({ ...props, id })}
            <EditRawInput {...props} id={id} tag={tag}>
              <InputGroup
                defaultValue={simpleSelectorGet(props.simpleRedux, [
                  `${tag}::${padId(id)}`,
                  "data"
                ])}
                onChange={onChangeFnCall(clickDirtyInputFns(props))}
                onClick={onChangeFnCall(clickDirtyInputFns(props))}
                onBlur={onChangeFnCall(
                  props.simpleMergeData,
                  `${tag}::${padId(id)}`
                )}
                placeholder={serverType}
              />
            </EditRawInput>
          </ControlGroup>
        </FormGroup>
      )
    } else if (typescriptType === TypescriptBaseTypes.string) {
      return (
        <FormGroup
          css={css(cssFormGroup)}
          label={formLabelFormatter(name, serverType)}
        >
          <ControlGroup>
            {...repeatableFieldButtons({ ...props, id })}
            <EditRawInput {...props} id={id} tag={tag}>
              <InputGroup
                defaultValue={simpleSelectorGet(props.simpleRedux, [
                  `${tag}::${padId(id)}`,
                  "data"
                ])}
                onBlur={onChangeFnCall(
                  props.simpleMergeData,
                  `${tag}::${padId(id)}`
                )}
                onChange={onChangeFnCall(clickDirtyInputFns(props))}
                onClick={onChangeFnCall(clickDirtyInputFns(props))}
                placeholder={serverType}
              />
            </EditRawInput>
          </ControlGroup>
        </FormGroup>
      )
    } else if (typescriptType === TypescriptBaseTypes.enum) {
      const { enumValues } = parseEnumType(serverType)
      return (
        <FormGroup
          css={css(cssFormGroup)}
          label={formLabelFormatter(name, serverType)}
        >
          <ControlGroup>
            {...repeatableFieldButtons({ ...props, id })}
            <EditRawInput {...props} id={id} tag={tag}>
              <HTMLSelect
                large={true}
                onChange={onChangeFnCall(
                  props.simpleMergeData,
                  `${tag}::${padId(id)}`
                )}
                onClick={onChangeFnCall(clickDirtyInputFns(props))}
                onBlur={onChangeFnCall(
                  props.simpleMergeData,
                  `${tag}::${padId(id)}`
                )}
                // Show empty option to prompt selection since first option is not automatically persisted
                options={[""].concat(enumValues)}
              />
            </EditRawInput>
          </ControlGroup>
        </FormGroup>
      )
    } else if (typescriptType === null && idChildren.size > 0) {
      if (
        idChildren.first() &&
        typesMetadata.get(idChildren.first() as string).typescriptType &&
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
          const whichFormData = simpleSelectorGet(
            props.simpleRedux,
            [`${tag}::RequestBodyFormInputType`, "data"],
            false
          )
            ? "RAW"
            : "FORM"
          return (
            <div>
              <ControlGroup fill={true}>
                <Button
                  active={whichFormData === "FORM"}
                  css={css(cssButton)}
                  icon={IconNames.FORM}
                  onClick={onChangeToggleFnCall(
                    props.simpleMergeToggle,
                    `${tag}::RequestBodyFormInputType`,
                    props.simpleRedux
                  )}
                  text={"Form"}
                />
                <Button
                  active={whichFormData === "RAW"}
                  css={css(cssButton)}
                  icon={IconNames.MORE}
                  onClick={onChangeToggleFnCall(
                    props.simpleMergeToggle,
                    `${tag}::RequestBodyFormInputType`,
                    props.simpleRedux
                  )}
                  text={"Raw"}
                />
              </ControlGroup>
              {whichFormData === "FORM" ? (
                <Card css={css(cssCard)}>
                  {idChildren.map((child: string) =>
                    fieldGroup(child, serverType)
                  )}
                </Card>
              ) : (
                <Card css={css(cssCard)}>
                  <TextArea
                    css={css(cssWrapTextArea)}
                    defaultValue={simpleSelectorGet(props.simpleRedux, [
                      `${tag}::RawRequestBody`,
                      "data"
                    ])}
                    fill={true}
                    growVertically={true}
                    onChange={onChangeFnCall(
                      props.simpleMergeData,
                      `${tag}::RawRequestBody`
                    )}
                    placeholder={
                      "Raw request body. This input will return a string or JSON."
                    }
                  />
                </Card>
              )}
            </div>
          )
        } else {
          return (
            <Card css={css(cssCard)}>
              {idChildren.map((child: string) => fieldGroup(child, serverType))}
            </Card>
          )
        }
      }
    } else {
      return (
        <FormGroup
          css={css(cssFormGroup)}
          label={formLabelFormatter(name, serverType)}
        >
          <ControlGroup>
            {...repeatableFieldButtons({ ...props, id })}
            <TextArea
              css={css(cssWrapTextArea)}
              fill={true}
              growVertically={true}
              onBlur={(event: any) => {
                props.simpleMergeData(
                  `${tag}::${padId(id)}`,
                  event.target.value
                )
                clickDirtyInputFns(props)()
              }}
              onChange={onClickFnCall(clickDirtyInputFns(props))}
              onClick={onClickFnCall(clickDirtyInputFns(props))}
              placeholder={
                "Unparseable type. This input will return a string or JSON."
              }
            />
          </ControlGroup>
        </FormGroup>
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
