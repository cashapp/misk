/** @jsx jsx */
import { Button, Tooltip } from "@blueprintjs/core"
import { IconNames } from "@blueprintjs/icons"
import { css, jsx } from "@emotion/react"
import { OrderedMap } from "immutable"
import { Dispatch, SetStateAction } from "react"
import { cssButton, cssTooltip, QuantityButton } from "../components"
import {
  addRepeatedField,
  handler,
  IActionTypes,
  ITypesFieldMetadata,
  recursivelySetDirtyInput,
  removeRepeatedField,
} from "../form-builder"

export const repeatableFieldButtons = (props: {
  noFormIdentifier: string
  id: string
  // Existing value of the field, used as defaultValue for Raw field
  fieldValue: any
  isOpenEditRawInput: boolean
  setIsOpenEditRawInput: Dispatch<SetStateAction<boolean>>
  types: IActionTypes
  typesMetadata: OrderedMap<string, ITypesFieldMetadata>
  setTypesMetadata: Dispatch<
    SetStateAction<OrderedMap<string, ITypesFieldMetadata>>
  >
}) => {
  const {
    id,
    noFormIdentifier,
    isOpenEditRawInput,
    setIsOpenEditRawInput,
    fieldValue,
    types,
    setTypesMetadata,
    typesMetadata,
  } = props
  const metadata = typesMetadata.get(id)
  const editRawButton = (
    <Tooltip
      css={css(cssTooltip)}
      content={"Edit raw input"}
      key={`editRaw${id}`}
      lazy={true}
    >
      <Button
        active={isOpenEditRawInput}
        css={css(cssButton)}
        defaultValue={fieldValue}
        icon={IconNames.MORE}
        onClick={() => setIsOpenEditRawInput(!isOpenEditRawInput)}
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
        onClick={handler.handle(() =>
          setTypesMetadata(
            recursivelySetDirtyInput(typesMetadata, id, !dirtyInput)
          )
        )}
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
        changeFn={() =>
          setTypesMetadata(addRepeatedField(types, typesMetadata, idParent))
        }
        content={"Add Field"}
        icon={IconNames.PLUS}
        key={`Add${id}`}
      />
    )
    const removeButton = idChildren.size > 1 && (
      <QuantityButton
        changeFn={() =>
          setTypesMetadata(removeRepeatedField(typesMetadata, id))
        }
        content={"Remove Field"}
        icon={IconNames.CROSS}
        key={`Remove${id}`}
      />
    )
    return [dirtyInputButton, editRawButton, addButton, removeButton]
  } else if (metadata && !metadata.repeated && metadata.typescriptType) {
    return [dirtyInputButton, editRawButton]
  } else if (metadata && id !== "0") {
    return [dirtyInputButton]
  } else if (metadata && metadata.id === "0") {
    return [
      <Button css={css(cssButton)}>{noFormIdentifier}</Button>,
      editRawButton,
    ]
  } else {
    return [<Button css={css(cssButton)} icon={IconNames.WARNING_SIGN} />]
  }
}
