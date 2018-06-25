import React from 'react';
import { Region } from 'frint-react';
const { Link, Route, Switch } = require('frint-router-react');
import { Alignment, Button, Navbar, NavbarGroup, NavbarHeading, NavbarDivider, Menu, MenuItem } from '@blueprintjs/core';

import menu, { menuItem } from '../menu';
import routes from '../routes';

if (false) {
// https://stackoverflow.com/questions/49962495/integrate-react-router-active-navlink-with-child-component
// fixes console error of nested a tags
  const MenuItemExt = ({ text, icon, url, activeOnlyWhenExact = true }: menuItem) => {
    return (
      <Route
        path={url}
        exact={activeOnlyWhenExact}
        children={({match, history}: any) => (
          <MenuItem
            active={match}
            icon={icon}
            onClick={() => {
              history.push(url);
            }}
            text={text}
          />
        )}
      />
    );
  };
}

export default class RootContainer extends React.Component {
  render() {
    return (
      <div>
        <Navbar>
          <NavbarGroup align={Alignment.LEFT}>
            <NavbarHeading>Misk Admin</NavbarHeading>
            <NavbarDivider/>
            {menu.map(({ text, icon="document", className="pt-minimal", url } : menuItem) => (
              <Link key={url} to={url}><Button key={url} className={className} icon={icon} text={text}/></Link>
            ))}
          </NavbarGroup>
        </Navbar>
        <div className="misk-menu">
          <Menu>
            {menu.map(({ text, icon="document", className="pt-minimal", url } : menuItem) => (
              <Link key={url} to={url}><MenuItem key={url} href={url} className={className} icon={icon} text={text}/></Link>
            ))}
            <Region name="mainMenu"/>
          </Menu>
        </div>
        <div className="container misk-main-container">
          <div className="row">
            <div className="twelve columns">
              <Switch>
                {routes.map(({ path, exact, component: C }) => (
                  <Route key={path} path={path} exact={exact} component={C}/>
                ))}
              </Switch>
            </div>
          </div>
        </div>
      </div>
    );
  }
}
