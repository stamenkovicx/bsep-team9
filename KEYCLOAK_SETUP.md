# Keycloak and Session Management Setup Guide

This guide explains how to set up and use both Keycloak authentication and JWT token session management features.

## Features Implemented

### 1. JWT Token Session Management
- **Active Session Tracking**: Users can view all their active login sessions
- **Session Information**: Shows device type, browser, IP address, and last activity
- **Session Revocation**: Users can revoke individual sessions or all sessions
- **Security**: Sessions are tracked without storing actual JWT tokens in the database

### 2. Keycloak Authentication
- **Enterprise Authentication**: Integration with Keycloak for enterprise-grade authentication
- **OAuth2 Flow**: Complete OAuth2 authorization code flow implementation
- **User Management**: Automatic user creation and management through Keycloak

## Backend Setup

### 1. Database Schema
The following tables are automatically created:
- `user_session` - Stores session metadata without actual tokens

### 2. Environment Variables
Add these environment variables to your `.env` file or system:

```bash
# JWT Configuration
JWT_SECRET=your-jwt-secret-key

# Keycloak Configuration (when using Keycloak profile)
KEYCLOAK_SERVER_URL=http://localhost:8080
KEYCLOAK_REALM=pki-system
KEYCLOAK_CLIENT_ID=pki-system-client
KEYCLOAK_CLIENT_SECRET=your-client-secret

# Database
DB_USERNAME=postgres
DB_PASSWORD=super

# Email Configuration
GMAIL_USERNAME=your-email@gmail.com
GMAIL_PASSWORD=your-app-password

# reCAPTCHA
RECAPTCHA_SECRET_KEY=your-recaptcha-secret
RECAPTCHA_SITE_KEY=your-recaptcha-site-key

# Keystore
KEYSTORE_MASTER_KEY=your-keystore-master-key
```

### 3. Running the Application

#### Standard JWT Mode (Default)
```bash
./mvnw spring-boot:run
```

#### Keycloak Mode
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=keycloak
```

## Keycloak Setup

### 1. Install Keycloak
Download and install Keycloak from https://www.keycloak.org/

### 2. Start Keycloak
```bash
# Default installation
bin/kc.sh start-dev

# Or with custom configuration
bin/kc.sh start-dev --http-port=8080 --hostname=localhost
```

### 3. Create Realm and Client
1. Access Keycloak admin console at http://localhost:8080
2. Create a new realm named `pki-system`
3. Create a client named `pki-system-client`
4. Configure the client:
   - Client ID: `pki-system-client`
   - Client Protocol: `openid-connect`
   - Access Type: `public`
   - Valid Redirect URIs: `http://localhost:8089/auth/keycloak/login-success`
   - Web Origins: `http://localhost:8089`

### 4. Create Users
1. Go to Users → Add User
2. Create test users with email addresses
3. Set passwords and enable accounts

## Frontend Setup

### 1. Install Dependencies
```bash
npm install
```

### 2. Environment Configuration
Update `src/env/environment.ts`:
```typescript
export const environment = {
  production: false,
  apiHost: 'http://localhost:8089/'
};
```

### 3. Run Frontend
```bash
ng serve
```

## Usage

### Session Management
1. Login to the application
2. Click on "Sessions" in the navigation bar
3. View all active sessions with device and browser information
4. Revoke individual sessions or all sessions

### Keycloak Authentication
1. Click "Login with Keycloak" button
2. Redirected to Keycloak login page
3. Enter credentials
4. Redirected back to application

## API Endpoints

### Session Management
- `GET /auth/sessions` - Get active sessions for current user
- `DELETE /auth/sessions/{sessionId}` - Revoke specific session
- `DELETE /auth/sessions/revoke-all` - Revoke all sessions

### Keycloak Authentication
- `GET /auth/keycloak/userinfo` - Get user information from Keycloak
- `GET /oauth2/authorization/keycloak` - Initiate OAuth2 flow

## Security Features

### Session Security
- Sessions are tracked using unique session IDs (JTI claims in JWT)
- No actual tokens are stored in the database
- Automatic cleanup of expired sessions
- Session validation on every request

### Keycloak Integration
- OAuth2 authorization code flow
- Automatic user provisioning
- Secure token handling
- Integration with existing user management

## Troubleshooting

### Common Issues

1. **Keycloak Connection Issues**
   - Verify Keycloak server is running
   - Check realm and client configuration
   - Ensure redirect URIs are correct

2. **Session Management Issues**
   - Check database connectivity
   - Verify JWT configuration
   - Check session cleanup jobs

3. **Frontend Issues**
   - Verify API endpoints are accessible
   - Check CORS configuration
   - Ensure proper authentication flow

### Logs
Check application logs for detailed error information:
```bash
tail -f logs/application.log
```

## Development Notes

- Session cleanup runs every hour automatically
- Keycloak integration is profile-based (use `keycloak` profile)
- Both authentication methods can coexist
- Frontend components are modular and reusable
