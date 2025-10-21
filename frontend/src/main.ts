import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';

import { AppModule } from './app/app.module';


window.addEventListener('unhandledrejection', (event) => {
  if (event.reason === 'Timeout') {
    event.preventDefault();
    console.warn('reCAPTCHA timeout ignored');
  }
});

platformBrowserDynamic().bootstrapModule(AppModule)
  .catch(err => console.error(err));
