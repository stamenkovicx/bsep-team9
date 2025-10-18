import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LoginComponent } from './login/login.component';
import { MaterialModule } from '../material/material.module';
import { ReactiveFormsModule } from '@angular/forms';
import { RegistrationComponent } from './registration/registration.component';
import { RecaptchaModule, RecaptchaFormsModule } from 'ng-recaptcha';
import { MatCardModule } from '@angular/material/card';
import { RouterModule } from '@angular/router';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { ChangePasswordRequiredComponent } from './change-password-required/change-password-required.component'; 





@NgModule({
  declarations: [
    LoginComponent,
    RegistrationComponent,
    ChangePasswordRequiredComponent
  ],
  imports: [
    CommonModule,
    MaterialModule,
    ReactiveFormsModule,
    RecaptchaModule,
    RecaptchaFormsModule,
    MatCardModule,
    RouterModule,
    MatFormFieldModule,
    MatInputModule
  ],
  exports: [
    LoginComponent,
    MatCardModule
  ]
})
export class AuthModule { }
