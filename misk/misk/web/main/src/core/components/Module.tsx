import React from 'react';
import { Region } from 'frint-react';

export namespace Module {
  export interface Props {
    match: {
      isExact: boolean,
      params: {
        moduleID: string
      },
      url: string
    }
  }
}

export default class Module extends React.Component<Module.Props> {
  constructor(props: Module.Props) {
    super(props)
  }

  async loadModule(moduleID: string): Promise<any> {
    return import(`./module_${moduleID}.js`)
  }

  render() {
    this.loadModule(this.props.match.params.moduleID);
    return (
      <div>
        <h1>Module: {this.props.match.params.moduleID}</h1>
        <Region name="main"/>
      </div>
    );
  }
}
