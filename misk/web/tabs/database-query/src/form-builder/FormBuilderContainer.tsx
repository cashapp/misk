import React, { Dispatch, SetStateAction, useState } from "react"
import {
  FormFieldBuilderContainer,
  generateTypesMetadata,
  getFormData,
  IActionTypes,
  newFieldValueStore
} from "."

export const FormBuilderContainer = (props: {
  formType: string
  noFormIdentifier: string
  setFormData: Dispatch<SetStateAction<object>>
  rawRequestBody: string
  setRawRequestBody: Dispatch<SetStateAction<string>>
  requestBodyFormInputType: boolean
  setRequestBodyFormInputType: Dispatch<SetStateAction<boolean>>
  setIsOpenRequestBodyPreview: Dispatch<SetStateAction<boolean>>
  types: IActionTypes
}) => {
  const {
    formType,
    noFormIdentifier,
    setFormData,
    requestBodyFormInputType,
    setRequestBodyFormInputType,
    setIsOpenRequestBodyPreview,
    rawRequestBody,
    setRawRequestBody,
    types
  } = props
  const [typesMetadata, setTypesMetadata] = useState(
    generateTypesMetadata(types, formType)
  )
  const [fieldValueStore, setFieldValueStore] = useState(newFieldValueStore())

  // Return back the form request body
  const formData = getFormData(fieldValueStore, typesMetadata)
  
  return (
    <FormFieldBuilderContainer
      id={"0"}
      noFormIdentifier={noFormIdentifier}
      types={types}
      setIsOpenRequestBodyPreview={setIsOpenRequestBodyPreview}
      fieldValueStore={fieldValueStore}
      setFieldValueStore={setFieldValueStore}
      setTypesMetadata={setTypesMetadata}
      typesMetadata={typesMetadata}
      requestBodyFormInputType={requestBodyFormInputType}
      setRequestBodyFormInputType={setRequestBodyFormInputType}
      rawRequestBody={rawRequestBody}
      setRawRequestBody={setRawRequestBody}
    />
  )
}
