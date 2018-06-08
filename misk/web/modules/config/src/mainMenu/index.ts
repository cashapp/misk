import App from './app';

((<any>window).app = (<any>window).app || []).push([App, {
  regions: ['mainMenu'],
}]);