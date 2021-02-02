import React, { Dispatch, SetStateAction, useEffect, useState } from "react"
import {
  FormFieldBuilderContainer,
  generateTypesMetadata,
  getFormData,
  IActionTypes,
  newFieldValueStore
} from "../form-builder"

export const FormBuilderContainer = (props: {
  formType: string
  noFormIdentifier: string
  rawRequestBody: string
  requestBodyFormInputType: boolean
  setIsOpenRequestBodyPreview: Dispatch<SetStateAction<boolean>>
  setFormData: Dispatch<SetStateAction<object>>
  setRawRequestBody: Dispatch<SetStateAction<string>>
  setRequestBodyFormInputType: Dispatch<SetStateAction<boolean>>
  types: IActionTypes
}) => {
  const {
    formType,
    noFormIdentifier,
    rawRequestBody,
    requestBodyFormInputType,
    setFormData,
    setIsOpenRequestBodyPreview,
    setRawRequestBody,
    setRequestBodyFormInputType,
    types
  } = props
  const [typesMetadata, setTypesMetadata] = useState(
    generateTypesMetadata(types, formType)
  )
  const [fieldValueStore, setFieldValueStore] = useState(newFieldValueStore())

  useEffect(() => {
    // Return back the form request body
    const formData = getFormData(fieldValueStore, typesMetadata)
    setFormData(formData)
  }, [fieldValueStore, typesMetadata])

  return (
    <FormFieldBuilderContainer
      fieldValueStore={fieldValueStore}
      id={"0"}
      noFormIdentifier={noFormIdentifier}
      rawRequestBody={rawRequestBody}
      requestBodyFormInputType={requestBodyFormInputType}
      setFieldValueStore={setFieldValueStore}
      setIsOpenRequestBodyPreview={setIsOpenRequestBodyPreview}
      setRawRequestBody={setRawRequestBody}
      setRequestBodyFormInputType={setRequestBodyFormInputType}
      setTypesMetadata={setTypesMetadata}
      types={types}
      typesMetadata={typesMetadata}
    />
  )
}
