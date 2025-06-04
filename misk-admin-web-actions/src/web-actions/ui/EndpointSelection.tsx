import React from 'react';
import Select, {
  OnChangeValue,
  StylesConfig,
  components,
  OptionProps,
} from 'react-select';
import { MiskRoute } from '@web-actions/api/responseTypes';
import RealMetadataClient from '@web-actions/api/RealMetadataClient';
import { appEvents, APP_EVENTS } from '@web-actions/events/appEvents';

export interface EndpointOption {
  value: MiskRoute;
  label: string;
  termsString: string;
}

interface Props {
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

  // TODO consider moving this to an isMiskEndpoint boolean on the metadata from the server
  private isMiskEndpoint(route: MiskRoute): boolean {
    const routes = [
      '/',
      '/_liveness',
      '/_readiness',
      '/_status',
      '/api/{id}/metadata',
      '/api/v1/database/query/metadata',
      '/api/service/metadata',
      '/{path:.*}',
    ];
    const routePrefixes = [
      '/_admin',
      '/v2/_admin',
      '/_tab',
      '/api/dashboard/',
      '/@misk',
      '/static',
    ];
    return (
      routes.includes(route.path) ||
      routePrefixes.some((prefix) => route.path.startsWith(prefix))
    );
  }

  componentDidMount() {
    this.metadataClient.fetchMetadata().then((actions: MiskRoute[]) => {
      const appEndpoints = actions
        .filter((action) => !this.isMiskEndpoint(action))
        .sort((a, b) => {
          // Sort by path first, then by HTTP method
          const pathCompare = a.path.localeCompare(b.path);
          if (pathCompare !== 0) return pathCompare;
          return a.httpMethod.localeCompare(b.httpMethod);
        });
      const miskEndpoints = actions
        .filter((action) => this.isMiskEndpoint(action))
        .sort((a, b) => {
          // Sort by path first, then by HTTP method
          const pathCompare = a.path.localeCompare(b.path);
          if (pathCompare !== 0) return pathCompare;
          return a.httpMethod.localeCompare(b.httpMethod);
        });

      // Combine app and Misk endpoints, ensuring Misk endpoints are always after app endpoints for less scrolling
      this.options = [...appEndpoints, ...miskEndpoints].map((actionGroup) => ({
        label: `${actionGroup.httpMethod} ${actionGroup.path} (${actionGroup.actionName})`,
        termsString:
          `${actionGroup.httpMethod} ${actionGroup.path} ${actionGroup.actionName}`.toLowerCase(),
        value: actionGroup,
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
        terms.every((term) => option.termsString.includes(term)),
      ),
    });
  }

  handleInputChange = (inputValue: string) => {
    this.setState({ inputValue });
  };

  handleChange = (value: OnChangeValue<EndpointOption, false>) => {
    if (value) {
      appEvents.emit(APP_EVENTS.ENDPOINT_SELECTED, value.value);
    }
  };

  handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Escape') {
      this.props?.onDismiss?.();
    }
  };

  setMenuOpen = (menuIsOpen: boolean) => {
    this.setState({ menuIsOpen });
  };

  focusSelect = () => {
    this.setMenuOpen(true);
    this.selectRef.current?.focus();
  };

  handleFocus = (_: React.FocusEvent<HTMLInputElement>) => {
    this.setMenuOpen(true);
  };

  render() {
    const Option = (props: OptionProps<EndpointOption, false>) => {
      const endpoint = props.data.value;
      return (
        <components.Option {...props}>
          {endpoint.httpMethod} {endpoint.path}{' '}
          <span style={{ color: '#888' }}>({endpoint.actionName})</span>
        </components.Option>
      );
    };

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
        onMenuOpen={() => this.setMenuOpen(true)}
        options={this.state.filteredOptions}
        components={{ Option }}
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
