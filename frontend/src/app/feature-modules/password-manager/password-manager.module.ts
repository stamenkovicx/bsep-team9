import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { MaterialModule } from 'src/app/infrastructure/material/material.module';
import { PasswordManagerRoutingModule } from './password-manager-routing.module';
import { PasswordListComponent } from './password-list/password-list.component';
import { PasswordCreateComponent } from './password-create/password-create.component';
import { PasswordShareComponent } from './password-share/password-share.component';

@NgModule({
  declarations: [
    PasswordListComponent,
    PasswordCreateComponent,
    PasswordShareComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MaterialModule,
    PasswordManagerRoutingModule
  ],
  exports: [
    PasswordListComponent,
    PasswordCreateComponent,
    PasswordShareComponent
  ]
})
export class PasswordManagerModule { }