import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LoginComponent } from './login/login.component';
import { MaterialModule } from '../material/material.module';
import { ReactiveFormsModule } from '@angular/forms';
import { RegistrationComponent } from './registration/registration.component';
import { RecaptchaModule, RecaptchaFormsModule } from 'ng-recaptcha';
import { RouterModule } from '@angular/router';
import { ChangePasswordRequiredComponent } from './change-password-required/change-password-required.component';
import { ForgotPasswordComponent } from './forgot-password/forgot-password.component';
import { ResetPasswordComponent } from './reset-password/reset-password.component';
import { UserSessionsComponent } from './user-sessions/user-sessions.component';
import { ConfirmationDialogComponent } from './user-sessions/confirmation-dialog.component';
import { KeycloakLoginComponent } from './keycloak-login/keycloak-login.component'; 





@NgModule({
  declarations: [
    LoginComponent,
    RegistrationComponent,
    ChangePasswordRequiredComponent,
    ForgotPasswordComponent,
    ResetPasswordComponent,
    UserSessionsComponent,
    ConfirmationDialogComponent,
    KeycloakLoginComponent
  ],
  imports: [
    CommonModule,
    MaterialModule,
    ReactiveFormsModule,
    RecaptchaModule,
    RecaptchaFormsModule,
    RouterModule
  ],
  exports: [
    LoginComponent,
    UserSessionsComponent,
    KeycloakLoginComponent
  ]
})
export class AuthModule { }
