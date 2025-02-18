import React from 'react';
import Select, { OnChangeValue, StylesConfig } from 'react-select';
import Fuse from 'fuse.js';
import { ActionGroup, MiskActions } from '@web-actions/api/responseTypes';
import RealMetadataClient from '@web-actions/api/RealMetadataClient';

export interface EndpointOption {
  value: ActionGroup;
  label: string;
}

export type EndpointSelectionCallbacks = ((value: ActionGroup) => void)[];

interface Props {
  endpointSelectionCallbacks?: EndpointSelectionCallbacks;
  onDismiss?: () => void;
}

interface State {
  endpointOptions: EndpointOption[];
  filterOptions: EndpointOption[];
  inputValue: string;
  menuIsOpen: boolean;
}

export default class EndpointSelection extends React.Component<Props, State> {
  private selectRef = React.createRef<any>();
  private fuse: Fuse<EndpointOption> | null = null;
  private metadataClient = new RealMetadataClient();

  constructor(props: Props) {
    super(props);

    this.state = {
      endpointOptions: [],
      filterOptions: [],
      inputValue: '',
      menuIsOpen: false,
    };
  }

  componentDidMount() {
    this.metadataClient.fetchMetadata().then((actions: MiskActions) => {
      const options = Object.entries(actions).map(([key, value]) => ({
        value,
        label: key,
      }));
      this.fuse = new Fuse(options, {
        keys: ['label'],
        threshold: 0.3,
        useExtendedSearch: true,
      });
      this.setState({
        endpointOptions: options,
        filterOptions: options,
      });
      this.focusSelect();
    });
  }

  componentDidUpdate(_: any, prevState: State) {
    if (
      prevState.inputValue !== this.state.inputValue ||
      prevState.endpointOptions !== this.state.endpointOptions
    ) {
      this.updateFilterOptions();
    }
  }

  updateFilterOptions() {
    const { inputValue, endpointOptions } = this.state;
    if (!inputValue.trim() || !this.fuse) {
      this.setState({ filterOptions: endpointOptions });
      return;
    }

    const results = this.fuse
      .search(inputValue)
      .sort((a, b) => b.score! - a.score!);
    this.setState({
      filterOptions: results.map((result) => result.item),
    });
  }

  handleInputChange = (inputValue: string) => {
    this.setState({ inputValue });
  };

  handleChange = (value: OnChangeValue<EndpointOption, false>) => {
    if (value) {
      for (const callback of this.props?.endpointSelectionCallbacks ?? []) {
        callback(value.value);
      }
    }
  };

  handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Escape') {
      this.props?.onDismiss?.();
    }
  };

  setMenuOpen = (menuIsOpen: boolean) => {
    this.setState((state) => ({ ...state, menuIsOpen }));
  };

  focusSelect = () => {
    this.setMenuOpen(true);
    this.selectRef.current?.focus();
  };

  handleFocus = (_: React.FocusEvent<HTMLInputElement>) => {
    this.setMenuOpen(true);
  };

  render() {
    const { filterOptions } = this.state;
    return (
      <Select<EndpointOption, false>
        ref={this.selectRef}
        placeholder={'🔍 Select Endpoint'}
        defaultValue={null}
        filterOption={() => true}
        onFocus={this.handleFocus}
        onKeyDown={this.handleKeyDown}
        menuIsOpen={this.state.menuIsOpen}
        onMenuClose={() => this.setMenuOpen(false)}
        onInputChange={this.handleInputChange}
        onChange={this.handleChange}
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
}
