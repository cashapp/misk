/** @jsx jsx */
import { H5, Menu } from "@blueprintjs/core"
import { jsx } from "@emotion/core"
import { Dispatch, SetStateAction, useContext, useState } from "react"
import { cssColumn, cssMetadataMenu } from "../components"
import { FormBuilderContainer } from "../form-builder"
import { IDatabaseQueryMetadataAPI } from "./DatabaseQueryInterfaces"
import { SetFormData } from "../containers"

/**
 * Collapse wrapped Send a Request form for each Web Action card
 */
export const QueryFormContainer = (props: {
  databaseQuery: IDatabaseQueryMetadataAPI
  isOpenRequestBodyPreview: boolean
    setIsOpenRequestBodyPreview: Dispatch<SetStateAction<boolean>>
}) => {
  const { databaseQuery, setIsOpenRequestBodyPreview } = props
  const setFormData = useContext(SetFormData)

  const [requestBodyFormInputType, setRequestBodyFormInputType] = useState(
    false
  )
  const [rawRequestBody, setRawRequestBody] = useState("{}")

  return (
    <div css={cssColumn}>
      <Menu css={cssMetadataMenu}>
        <H5>{"Query"}</H5>
        {
          <FormBuilderContainer
            formType={"QueryRequest"}
            noFormIdentifier={`QueryRequest`}
            types={databaseQuery.types}
            rawRequestBody={rawRequestBody}
            setRawRequestBody={setRawRequestBody}
            requestBodyFormInputType={requestBodyFormInputType}
            setRequestBodyFormInputType={setRequestBodyFormInputType}
            setIsOpenRequestBodyPreview={setIsOpenRequestBodyPreview}
            setFormData={setFormData}
          />
        }
      </Menu>
    </div>
  )
}
