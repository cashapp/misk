import React, { useState } from "react"
import {
  FormFieldBuilderContainer,
  generateTypesMetadata,
  IActionTypes
} from "."

export const FormBuilderContainer = (props: {
  formType: string
  noFormIdentifier: string
  types: IActionTypes
}) => {
  const { formType, noFormIdentifier, types } = props
  const [typesMetadata, setTypesMetadata] = useState(
    generateTypesMetadata(types, formType)
  )
  console.log("yooooo", types, typesMetadata.toJS())
  return (
    <FormFieldBuilderContainer
      id={"0"}
      noFormIdentifier={noFormIdentifier}
      setTypesMetadata={setTypesMetadata}
      typesMetadata={typesMetadata}
    />
  )
}
