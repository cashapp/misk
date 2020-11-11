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
  TextArea
} from "@blueprintjs/core"
import { css, jsx } from "@emotion/core"
import { WrapTextContainer } from "@misk/core"
import { OrderedMap } from "immutable"
import { Dispatch, useState, SetStateAction } from "react"
import {
  BaseFieldTypes,
  EditRawInput,
  handler,
  ITypesFieldMetadata,
  repeatableFieldButtons,
  ServerTypes,
  TypescriptBaseTypes
} from "."
import { cssButton, cssWrapTextArea, Metadata } from "../components"
import {
  getFieldData,
  parseEnumType,
  recursivelySetDirtyInput
} from "./FormBuilderStore"
import { IActionTypes } from "./Interfaces"

const cssCard = css`
  box-shadow: none !important;
  border-left: 2px black;
  margin-bottom: 0px;
  padding: 0px 0px 0px 15px !important;
`

const cssFormGroup = css`
  margin: 0px !important;
`

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

const clickDirtyInputFns = (props: {
  id: string
  setIsOpenRequestBodyPreview: Dispatch<SetStateAction<boolean>>
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>
  setTypesMetadata: Dispatch<
    SetStateAction<OrderedMap<string, ITypesFieldMetadata>>
  >
}) => () => {
  const {
    id,
    setIsOpenRequestBodyPreview,
    setTypesMetadata,
    typesMetadata
  } = props
  setTypesMetadata(recursivelySetDirtyInput(typesMetadata, id, true))
  setIsOpenRequestBodyPreview(true)
}

const updateFieldValue = (
  id: string,
  fieldValueStore: OrderedMap<string, any>,
  setFieldValueStore: Dispatch<SetStateAction<OrderedMap<string, any>>>
) => (value: any) => {
  setFieldValueStore(fieldValueStore.set(id, value))
}

export const FormFieldBuilderContainer = (props: {
  id: string
  noFormIdentifier: string
  fieldValueStore: OrderedMap<string, any>
  setFieldValueStore: Dispatch<SetStateAction<OrderedMap<string, any>>>
  types: IActionTypes
  setTypesMetadata: Dispatch<
    SetStateAction<OrderedMap<string, ITypesFieldMetadata>>
  >
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>
  setIsOpenRequestBodyPreview: Dispatch<SetStateAction<boolean>>
  requestBodyFormInputType: boolean
  setRequestBodyFormInputType: Dispatch<SetStateAction<boolean>>
  rawRequestBody: string
  setRawRequestBody: Dispatch<SetStateAction<string>>
}) => {
  const {
    id,
    fieldValueStore,
    setFieldValueStore,
    noFormIdentifier,
    setTypesMetadata,
    typesMetadata,
    types,
    setIsOpenRequestBodyPreview,
    requestBodyFormInputType,
    setRequestBodyFormInputType,
    rawRequestBody,
    setRawRequestBody
  } = props
  const fieldValue = fieldValueStore.get(id)
  const setFieldValue = updateFieldValue(
    id,
    fieldValueStore,
    setFieldValueStore
  )

  // Field block state
  const [isOpenEditRawInput, setIsOpenEditRawInput] = useState(false)

  const metadata = typesMetadata.get(id)
  if (metadata) {
    // return <CodePreContainer>{metadata}</CodePreContainer>
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
            {...repeatableFieldButtons({
              noFormIdentifier,
              id,
              fieldValue,
              isOpenEditRawInput,
              setIsOpenEditRawInput,
              types,
              typesMetadata,
              setTypesMetadata
            })}
            <EditRawInput
              isOpen={isOpenEditRawInput}
              rawInput={fieldValue}
              setRawInput={setFieldValue}
            >
              <Button
                css={css(cssButton)}
                defaultValue={fieldValue}
                intent={
                  (fieldValue == null && false) || fieldValue
                    ? Intent.PRIMARY
                    : Intent.WARNING
                }
                onChange={clickDirtyInputFns({
                  id,
                  setIsOpenRequestBodyPreview,
                  setTypesMetadata,
                  typesMetadata
                })}
                onClick={() => {
                  setFieldValue(!fieldValue)
                  clickDirtyInputFns({
                    id,
                    setIsOpenRequestBodyPreview,
                    setTypesMetadata,
                    typesMetadata
                  })()
                }}
              >
                {((fieldValue == null && "unset") || fieldValue).toString()}
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
            {...repeatableFieldButtons({
              noFormIdentifier,
              id,
              fieldValue,
              isOpenEditRawInput,
              setIsOpenEditRawInput,
              types,
              typesMetadata,
              setTypesMetadata
            })}
            <EditRawInput
              isOpen={isOpenEditRawInput}
              rawInput={fieldValue}
              setRawInput={setFieldValue}
            >
              <InputGroup
                defaultValue={fieldValue}
                onChange={clickDirtyInputFns({
                  id,
                  setIsOpenRequestBodyPreview,
                  setTypesMetadata,
                  typesMetadata
                })}
                onClick={clickDirtyInputFns({
                  id,
                  setIsOpenRequestBodyPreview,
                  setTypesMetadata,
                  typesMetadata
                })}
                onBlur={handler.handle(setFieldValue)}
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
            {...repeatableFieldButtons({
              noFormIdentifier,
              id,
              fieldValue,
              isOpenEditRawInput,
              setIsOpenEditRawInput,
              types,
              typesMetadata,
              setTypesMetadata
            })}
            <EditRawInput
              isOpen={isOpenEditRawInput}
              rawInput={fieldValue}
              setRawInput={setFieldValue}
            >
              <InputGroup
                defaultValue={fieldValue}
                onBlur={handler.handle(setFieldValue)}
                onChange={clickDirtyInputFns({
                  id,
                  setIsOpenRequestBodyPreview,
                  setTypesMetadata,
                  typesMetadata
                })}
                onClick={clickDirtyInputFns({
                  id,
                  setIsOpenRequestBodyPreview,
                  setTypesMetadata,
                  typesMetadata
                })}
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
            {...repeatableFieldButtons({
              noFormIdentifier,
              id,
              fieldValue,
              isOpenEditRawInput,
              setIsOpenEditRawInput,
              types,
              typesMetadata,
              setTypesMetadata
            })}
            <EditRawInput
              isOpen={isOpenEditRawInput}
              rawInput={fieldValue}
              setRawInput={setFieldValue}
            >
              <HTMLSelect
                large={true}
                onChange={handler.handle(setFieldValue)}
                onClick={clickDirtyInputFns({
                  id,
                  setIsOpenRequestBodyPreview,
                  setTypesMetadata,
                  typesMetadata
                })}
                onBlur={handler.handle(setFieldValue)}
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
              <FormFieldBuilderContainer
                noFormIdentifier={noFormIdentifier}
                id={child}
                key={child}
                fieldValueStore={fieldValueStore}
                setFieldValueStore={setFieldValueStore}
                types={types}
                typesMetadata={typesMetadata}
                setTypesMetadata={setTypesMetadata}
                setIsOpenRequestBodyPreview={setIsOpenRequestBodyPreview}
                requestBodyFormInputType={requestBodyFormInputType}
                setRequestBodyFormInputType={setRequestBodyFormInputType}
                rawRequestBody={rawRequestBody}
                setRawRequestBody={setRawRequestBody}
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
                <FormFieldBuilderContainer
                  noFormIdentifier={noFormIdentifier}
                  id={child}
                  key={child}
                  fieldValueStore={fieldValueStore}
                  setFieldValueStore={setFieldValueStore}
                  types={types}
                  typesMetadata={typesMetadata}
                  setTypesMetadata={setTypesMetadata}
                  setIsOpenRequestBodyPreview={setIsOpenRequestBodyPreview}
                  requestBodyFormInputType={requestBodyFormInputType}
                  setRequestBodyFormInputType={setRequestBodyFormInputType}
                  rawRequestBody={rawRequestBody}
                  setRawRequestBody={setRawRequestBody}
                />
              </div>
            )
          } else {
            return (
              <div key={child}>
                <Label>{formLabelFormatter(childName, childServerType)}</Label>
                <ButtonGroup>
                  {...repeatableFieldButtons({
                    noFormIdentifier,
                    id: child,
                    fieldValue,
                    isOpenEditRawInput,
                    setIsOpenEditRawInput,
                    types,
                    typesMetadata,
                    setTypesMetadata
                  })}
                </ButtonGroup>
                {/* Allow editing raw an entire message block */}
                <EditRawInput
                  isOpen={isOpenEditRawInput}
                  rawInput={JSON.stringify(
                    getFieldData(fieldValueStore, typesMetadata, id)
                  )}
                  // tODO fix
                  setRawInput={setFieldValue}
                >
                  <FormFieldBuilderContainer
                    noFormIdentifier={noFormIdentifier}
                    id={child}
                    key={child}
                    fieldValueStore={fieldValueStore}
                    setFieldValueStore={setFieldValueStore}
                    types={types}
                    typesMetadata={typesMetadata}
                    setTypesMetadata={setTypesMetadata}
                    setIsOpenRequestBodyPreview={setIsOpenRequestBodyPreview}
                    requestBodyFormInputType={requestBodyFormInputType}
                    setRequestBodyFormInputType={setRequestBodyFormInputType}
                    rawRequestBody={rawRequestBody}
                    setRawRequestBody={setRawRequestBody}
                  />
                </EditRawInput>
              </div>
            )
          }
        }
        // Custom logic for root
        if (id === "0") {
          return (
            <div>
              <ButtonGroup>
                {...repeatableFieldButtons({
                  noFormIdentifier,
                  id,
                  fieldValue,
                  isOpenEditRawInput,
                  setIsOpenEditRawInput,
                  types,
                  typesMetadata,
                  setTypesMetadata
                })}
              </ButtonGroup>
              {/* Allow editing raw an entire message block */}
              <EditRawInput
                isOpen={isOpenEditRawInput}
                rawInput={JSON.stringify(
                  getFieldData(fieldValueStore, typesMetadata, id)
                )}
                // tODO fix
                setRawInput={setFieldValue}
              >
                <Card css={css(cssCard)}>
                  {idChildren.map((child: string) =>
                    fieldGroup(child, serverType)
                  )}
                </Card>
              </EditRawInput>
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
            {...repeatableFieldButtons({
              noFormIdentifier,
              id,
              fieldValue,
              isOpenEditRawInput,
              setIsOpenEditRawInput,
              types,
              typesMetadata,
              setTypesMetadata
            })}
            <TextArea
              css={css(cssWrapTextArea)}
              fill={true}
              growVertically={true}
              onBlur={handler.handle((value: any) => {
                setFieldValue(value)
                clickDirtyInputFns({
                  id,
                  setIsOpenRequestBodyPreview,
                  setTypesMetadata,
                  typesMetadata
                })()
              })}
              onChange={clickDirtyInputFns({
                id,
                setIsOpenRequestBodyPreview,
                setTypesMetadata,
                typesMetadata
              })}
              onClick={clickDirtyInputFns({
                id,
                setIsOpenRequestBodyPreview,
                setTypesMetadata,
                typesMetadata
              })}
              placeholder={
                "Unparseable type. This input will return a string or JSON."
              }
            />
          </ControlGroup>
        </FormGroup>
      )
    }
  } else {
    return <Metadata content={`No form for ${noFormIdentifier}`} />
  }
}
