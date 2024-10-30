import Select, { OnChangeValue } from 'react-select';
import { useEffect, useState } from 'react';
import React from 'react';
import {
  MiskActions,
  MiskWebActionDefinition,
} from '@misk-console/api/responseTypes';
import RealMetadataClient from '@misk-console/api/RealMetadataClient';
import { StylesConfig } from 'react-select/dist/declarations/src/styles';
import Fuse from 'fuse.js';

export interface EndpointOption {
  value: MiskWebActionDefinition;
  label: string;
}

export type EndpointSelectionCallbacks = ((
  value: MiskWebActionDefinition,
) => void)[];

interface Props {
  endpointSelectionCallbacks: EndpointSelectionCallbacks;
}

const metadataClient = new RealMetadataClient();

export default function EndpointSelection(props: Props) {
  const [endpointOptions, setEndpointOptions] = useState<EndpointOption[]>([]);
  const [filterOptions, setFilterOptions] = useState<EndpointOption[]>([]);
  const [inputValue, setInputValue] = useState<string>('');
  const ref = React.createRef<any>();
  const fuse = React.useRef<Fuse<EndpointOption>>();

  useEffect(() => {
    metadataClient.fetchMetadata().then((actions: MiskActions) => {
      const options = Object.entries(actions).map(([key, value]) => ({
        value,
        label: key,
      }));
      fuse.current = new Fuse(options, {
        keys: ['label'],
        threshold: 0.3,
        useExtendedSearch: true,
      });
      setEndpointOptions(options);
      ref.current?.focus();
    });
  }, []);

  useEffect(() => {
    if (!inputValue.trim() || fuse.current === null) {
      return setFilterOptions(endpointOptions);
    }

    const results = fuse
      .current!.search(inputValue)
      .sort((a, b) => b.score! - a.score!);
    setFilterOptions(results.map((result) => result.item));
  }, [endpointOptions, inputValue, fuse.current]);

  return (
    <Select<EndpointOption, false>
      ref={ref}
      placeholder={'🔍 Select Endpoint'}
      defaultValue={null}
      filterOption={() => true}
      onInputChange={setInputValue}
      onChange={(value: OnChangeValue<EndpointOption, false>) => {
        if (value) {
          for (const callback of props.endpointSelectionCallbacks) {
            callback(value.value);
          }
        }
      }}
      options={filterOptions}
      styles={
        {
          container: (base) => ({ ...base, width: '100%' }),
          menuPortal: (base) => ({ ...base, zIndex: 101 }),
        } as StylesConfig<any>
      }
      menuPortalTarget={document.body}
    />
  );
}
