import React from 'react';
import { observe, streamProps } from 'frint-react';
const { Link } = require('frint-router-react');
import { Menu, MenuItem, MenuDivider } from '@blueprintjs/core';

export namespace MenuComponent {
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

class MenuComponent extends React.Component<MenuComponent.Props> {
  render() {
    return (
      <div>
        <Link to="/threads"><MenuItem icon="comparison" text="Threads" /></Link>
            <Link to="/threads"><MenuItem icon="new-object" text="Other Threads Module" /></Link>
        <MenuDivider/>
      </div>
    );
  }
}

export default MenuComponent;
