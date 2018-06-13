import { App as FrintApp } from 'frint';
import { render } from 'frint-react';

import '../styles/main.css';

import App from './app';
// @TODO: create custom interface for window
const apps: FrintApp[]= (<any>window).app || [];
const app: FrintApp = (<any>window).app = new App();
// @TODO: revert back to app.registerApp(...options) once wrong # param error is fixed
// @TODO: add push interface to app
(<any>app).push = (options: any) => (<any>app).registerApp(...options);
apps.forEach((<any>app).push);

render(app, document.getElementById('root'));
