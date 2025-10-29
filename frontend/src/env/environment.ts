export const environment = {
    production: false,
    // Backend running on HTTPS at port 8089
    apiHost: 'https://localhost:8089/',
    recaptchaSiteKey: '6Ld-SusrAAAAANfM6_Nsp8_WUv7-yOXjFngQKrwF',
    keycloak: {
      url: 'http://localhost:8080',
      realm: 'pki-realm',
      clientId: 'pki-frontend',
      checkLoginIframe: false,
      enableLogging: true
    }
  };
  