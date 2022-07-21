import React, {useState} from "react"
import {
  Button,
  Collapse,
  HTMLSelect,
  Icon,
  InputGroup,
  NumericInput,
  Tooltip
} from "@blueprintjs/core"
import _ from "lodash"
import {ProtoField, WebActionMetadata} from "./types"
import {IOptionProps} from "@blueprintjs/core/lib/esm/common/props"

export type FormComponentProps<T> = {
  webActionMetadata: WebActionMetadata
  field: ProtoField
  value: T
  onChange: (value: T) => void
}

export function FormComponent(props: FormComponentProps<any>) {
  const { webActionMetadata, field } = props

  let delegate = null

  if (field.repeated) {
    delegate = <FormRepeatedComponent {...props} />
  } else if (field.type === "String" || field.type === "ByteString") {
    delegate = <FormTextComponent {...props} />
  } else if (field.type === "Int" || field.type === "Long") {
    delegate = <FormNumberComponent {...props} />
  } else if (field.type === "Boolean") {
    delegate = <FormBoolComponent {...props} />
  } else if (field.type.startsWith("Enum")) {
    delegate = <FormEnumComponent {...props} />
  } else if (webActionMetadata.types[field.type]) {
    delegate = <FormObjectComponent {...props} />
  }

  return (
    delegate || (
      <p style={{ color: "red" }}>Unsupported field type {field.type}.</p>
    )
  )
}

function FormTextComponent({
  field,
  value,
  onChange
}: FormComponentProps<string>) {
  return (
    <FormComponentWrapper
      onClear={() => onChange(null)}
      protoName={field.name}
      protoType={field.type}
    >
      <InputGroup
        placeholder={field.type}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
          onChange(e.currentTarget.value)
        }
        value={value || ""}
      />
    </FormComponentWrapper>
  )
}

function FormNumberComponent({
  field,
  value,
  onChange
}: FormComponentProps<number>) {
  return (
    <FormComponentWrapper
      onClear={() => onChange(null)}
      protoName={field.name}
      protoType={field.type}
    >
      <NumericInput
        buttonPosition="none"
        placeholder={field.type}
        defaultValue={null}
        onValueChange={(_, s: string) => {
          onChange(s ? Number(s) : null)
        }}
        value={value || ""}
      />
    </FormComponentWrapper>
  )
}

function FormBoolComponent({
  field,
  value,
  onChange
}: FormComponentProps<boolean>) {
  return (
    <FormComponentWrapper
      onClear={() => onChange(null)}
      protoName={field.name}
      protoType={field.type}
    >
      <HTMLSelect
        options={["", "True", "False"]}
        onChange={(e: React.ChangeEvent<HTMLSelectElement>) => {
          const val = e.currentTarget.value
          onChange(val === "" ? null : val === "True")
        }}
        value={value === null ? "" : value ? "True" : "False"}
      />
    </FormComponentWrapper>
  )
}

function FormEnumComponent({
  field,
  value,
  onChange
}: FormComponentProps<string>) {
  const enumValues = field.type
    .replace(">", "")
    .substring(5)
    .split(",")
  const protoType = enumValues.shift()
  // Add a blank value to use to not include in the request.
  const options: IOptionProps[] = [{ label: "", value: null }]
  enumValues.forEach(val => {
    options.push({ label: val, value: val })
  })
  return (
    <FormComponentWrapper
      onClear={() => onChange(null)}
      protoName={field.name}
      protoType={protoType}
    >
      <HTMLSelect
        options={options}
        onChange={(e: React.ChangeEvent<HTMLSelectElement>) => {
          onChange(e.currentTarget.value || null)
        }}
        value={value || ""}
      />
    </FormComponentWrapper>
  )
}

function FormRepeatedComponent({
  webActionMetadata,
  field,
  value,
  onChange
}: FormComponentProps<any[]>) {
  const addButton = (
    <Button
      text="ADD"
      intent="primary"
      small={true}
      minimal={true}
      style={{ margin: 0 }}
      onClick={() => {
        onChange(value ? [...value, null] : [null])
      }}
    />
  )

  return (
    <FormComponentWrapper
      collapsable
      onClear={() => onChange(null)}
      protoName={field.name}
      protoType={`${field.type}[]`}
      buttons={addButton}
    >
      {_.isEmpty(value) && (
        <span style={{ fontStyle: "italic" }}>empty []</span>
      )}
      <div style={{ marginLeft: "12px" }}>
        {value &&
          value.map((cur, index) => (
            <FormComponent
              webActionMetadata={webActionMetadata}
              field={{
                ...field,
                name: `${field.name}[${index}] `,
                repeated: false
              }}
              onChange={newChildVal => {
                if (newChildVal === null) {
                  const newVal = [...value]
                  newVal.splice(index, 1)
                  onChange(newVal.length === 0 ? null : newVal)
                } else {
                  const newListVal = [...value]
                  newListVal[index] = newChildVal
                  onChange(newListVal)
                }
              }}
              value={cur}
            />
          ))}
      </div>
    </FormComponentWrapper>
  )
}

function FormObjectComponent({
  webActionMetadata,
  field,
  value,
  onChange
}: FormComponentProps<any>) {
  const complexType = webActionMetadata.types[field.type]
  return (
    <FormComponentWrapper
      collapsable
      onClear={() => onChange(null)}
      protoName={field.name}
      protoType={field.type}
    >
      <div style={{ marginLeft: "12px" }}>
        {complexType.fields.map(subField => {
          return (
            <FormComponent
              webActionMetadata={webActionMetadata}
              field={subField}
              onChange={newChildVal => {
                const newValue = _(_.clone(value) || {})
                  .set(subField.name, newChildVal)
                  .omitBy(_.isNull)
                  .value()

                if (_.isEmpty(newValue)) {
                  onChange(null)
                } else {
                  onChange(newValue)
                }
              }}
              value={_.get(value, subField.name, null)}
            />
          )
        })}
      </div>
    </FormComponentWrapper>
  )
}

type FormComponentWrapperProps = {
  protoName: string
  protoType: string
  onClear: () => void
  buttons?: React.ReactNode
  children: React.ReactNode
  collapsable?: boolean
}

function FormComponentWrapper({
  collapsable = false,
  protoName,
  protoType,
  onClear,
  buttons,
  children
}: FormComponentWrapperProps) {
  const [isOpen, setOpen] = useState(true)

  const label = (
    <div style={{ display: "flex", alignItems: "center" }}>
      <span
        style={{ fontWeight: "bold", flexGrow: 1 }}
        onClick={() => collapsable && setOpen(!isOpen)}
      >
        {protoName} ({protoType.split(".").pop()})
        {collapsable && <Icon icon={isOpen ? "caret-down" : "caret-right"} />}
      </span>
      {buttons}
      <Tooltip content={`clear ${protoName}`}>
        <Button
          tabIndex={-1}
          intent={"primary"}
          minimal
          style={{ margin: "0" }}
          onClick={onClear}
          icon={"cross"}
        />
      </Tooltip>
    </div>
  )
  return (
    <div>
      {label}
      <Collapse isOpen={isOpen} keepChildrenMounted>
        {children}
      </Collapse>
    </div>
  )
}
