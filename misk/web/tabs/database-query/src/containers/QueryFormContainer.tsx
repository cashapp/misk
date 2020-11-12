/** @jsx jsx */
import { H5, Menu } from "@blueprintjs/core"
import { jsx } from "@emotion/core"
import { useContext, useState } from "react"
import { cssColumn, cssMetadataMenu } from "../components"
import { FormBuilderContainer } from "../form-builder"
import { IDatabaseQueryMetadataAPI } from "./DatabaseQueryInterfaces"
import { SetFormData } from "../containers"

/**
 * Collapse wrapped Send a Request form for each Web Action card
 */
export const QueryFormContainer = (props: {
  databaseQuery: IDatabaseQueryMetadataAPI
}) => {
  const { databaseQuery } = props
  const setFormData = useContext(SetFormData)

  const [, setIsOpenRequestBodyPreview] = useState(false)
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

        {/* {databaseQuery.orders.length > 0 && <H5>{"Orders"}</H5>}
            {databaseQuery.orders.map((order: IOrderMetadata) => (
              <FormBuilderContainer
                formType={order.parametersType}
                noFormIdentifier={`${databaseQuery.queryClass} ${order.name}`}
                types={databaseQuery.types}
                rawRequestBody={rawRequestBody}
                  setRawRequestBody={setRawRequestBody}
                  requestBodyFormInputType={requestBodyFormInputType}
                  setRequestBodyFormInputType={setRequestBodyFormInputType}
                  setIsOpenRequestBodyPreview={setIsOpenRequestBodyPreview}
                  setFormData={setFormRequestBody}
              />
            ))}
            {databaseQuery.selects.length > 0 && <H5>{"Selects"}</H5>}
            {databaseQuery.selects.map((select: ISelectMetadata) => (
              <FormBuilderContainer
                formType={select.parametersType}
                noFormIdentifier={`${databaseQuery.queryClass} ${select.name}`}
                types={databaseQuery.types}
                rawRequestBody={rawRequestBody}
                  setRawRequestBody={setRawRequestBody}
                  requestBodyFormInputType={requestBodyFormInputType}
                  setRequestBodyFormInputType={setRequestBodyFormInputType}
                  setIsOpenRequestBodyPreview={setIsOpenRequestBodyPreview}
                  setFormData={setFormRequestBody}
              />
            ))} */}
      </Menu>
    </div>
  )
}
