import React from 'react';
import Select, { OnChangeValue, StylesConfig } from 'react-select';
import { ActionGroup, MiskActions } from '@web-actions/api/responseTypes';
import RealMetadataClient from '@web-actions/api/RealMetadataClient';

export interface EndpointOption {
  value: ActionGroup;
  label: string;
  lowerCaseLabel: string;
}

export type EndpointSelectionCallbacks = ((value: ActionGroup) => void)[];

interface Props {
  endpointSelectionCallbacks?: EndpointSelectionCallbacks;
  onDismiss?: () => void;
}

interface State {
  filteredOptions: EndpointOption[];
  inputValue: string;
  menuIsOpen: boolean;
}

export default class EndpointSelection extends React.Component<Props, State> {
  private selectRef = React.createRef<any>();
  private metadataClient = new RealMetadataClient();
  private options: EndpointOption[] = [];

  constructor(props: Props) {
    super(props);

    this.state = {
      filteredOptions: [],
      inputValue: '',
      menuIsOpen: false,
    };
  }

  componentDidMount() {
    this.metadataClient.fetchMetadata().then((actions: MiskActions) => {
      this.options = Object.values(actions)
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((it) => ({
          label: it.name,
          lowerCaseLabel: it.name.toLowerCase(),
          value: it,
        }));
      this.setState({ filteredOptions: this.options });
      this.focusSelect();
    });
  }

  componentDidUpdate(_: any, prevState: State) {
    if (prevState.inputValue !== this.state.inputValue) {
      this.updateFilterOptions();
    }
  }

  updateFilterOptions() {
    const terms = this.state.inputValue
      .split(/\s+/)
      .filter((it) => it.length > 0)
      .map((it) => it.toLowerCase());
    if (terms.length === 0) {
      this.setState({ filteredOptions: this.options });
      return;
    }

    this.setState({
      filteredOptions: this.options.filter((option) =>
        terms.every((term) => option.lowerCaseLabel.includes(term)),
      ),
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
        options={this.state.filteredOptions}
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
