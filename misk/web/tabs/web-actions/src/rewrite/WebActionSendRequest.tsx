import React, { useState } from "react"
import {
  Button,
  FormGroup,
  InputGroup,
  H5,
  HTMLSelect,
  NumericInput
} from "@blueprintjs/core"
import _ from "lodash"
import { WebActionMetadata, ProtoField } from "./types"
import axios from "axios"

interface Props {
  webActionMetadata: WebActionMetadata
}

export default function WebActionSendRequest({ webActionMetadata }: Props) {
  const [request, setRequest] = useState<any>({})
  const [loading, setLoading] = useState(false)
  const [response, setResponse] = useState("")
  const requestType = webActionMetadata.types[webActionMetadata.requestType]

  const handleSubmit = () => {
    setLoading(true)
    axios
      .post(webActionMetadata.pathPattern, JSON.stringify(request))
      .then(response => {
        setResponse(response.data)
      })
      .catch(e => {
        setResponse(e.message)
      })
      .finally(() => {
        setLoading(false)
      })
  }

  const handleOnChange = (path: string[], value: any) => {
    setRequest((currentValue: any) => {
      const copyOfState = _.clone(currentValue)
      _.set(copyOfState, path, value)
      return copyOfState
    })
  }

  const toFormElement = (field: ProtoField, path: string[]) => {
    if (field.type === "String" || field.type === "ByteString") {
      return (
        <FormGroup label={`${field.name} (${field.type})`}>
          <InputGroup
            placeholder={field.type}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              handleOnChange(path.concat(field.name), e.currentTarget.value)
            }
          />
        </FormGroup>
      )
    }

    if (field.type === "Int" || field.type === "Long") {
      return (
        <FormGroup label={`${field.name} (${field.type})`}>
          <NumericInput
            buttonPosition="none"
            placeholder={field.type}
            onValueChange={(e: number) =>
              handleOnChange(path.concat(field.name), e)
            }
          />
        </FormGroup>
      )
    }

    if (field.type === "Boolean") {
      return (
        <FormGroup label={`${field.name} (${field.type})`}>
          <HTMLSelect
            options={["", "True", "False"]}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
              handleOnChange(
                path.concat(field.name),
                e.currentTarget.value === "True"
              )
            }
          />
        </FormGroup>
      )
    }

    if (field.type.startsWith("Enum")) {
      // Looks like Enum<com.squareup.protos.cash.loanstar.data.AutoPay,ENABLED,DISABLED>.
      const enumValues = field.type
        .replace(">", "")
        .substring(5)
        .split(",")
      const protoName = enumValues.shift()
      enumValues.unshift("") // Add a blank value to use to not include in the request.
      return (
        <FormGroup label={`${field.name} (${protoName})`}>
          <HTMLSelect
            options={enumValues}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
              handleOnChange(path.concat(field.name), e.currentTarget.value)
            }
          />
        </FormGroup>
      )
    }

    const complexType = webActionMetadata.types[field.type]
    return (
      <FormGroup label={`${field.name} (${field.type})`}>
        <div style={{ marginLeft: "8px" }}>
          {complexType.fields.map(subField =>
            toFormElement(subField, path.concat(field.name))
          )}
        </div>
      </FormGroup>
    )
  }

  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        columnGap: "16px"
      }}
    >
      <div>
        <H5>Request</H5>
        {requestType.fields.map(field => toFormElement(field, []))}
        <Button intent="success" onClick={handleSubmit} loading={loading}>
          Submit
        </Button>
      </div>
      <div>
        <H5>Preview Request Body</H5>
        <pre>{JSON.stringify(request, null, 2)}</pre>

        <H5>Response</H5>
        <pre>{response}</pre>
      </div>
    </div>
  )
}
