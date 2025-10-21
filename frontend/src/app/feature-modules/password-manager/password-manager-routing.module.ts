import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PasswordListComponent } from './password-list/password-list.component';
import { PasswordCreateComponent } from './password-create/password-create.component';
import { PasswordShareComponent } from './password-share/password-share.component';

const routes: Routes = [
  { path: 'passwords', component: PasswordListComponent },
  { path: 'passwords/create', component: PasswordCreateComponent },
  { path: 'passwords/edit/:id', component: PasswordCreateComponent },
  { path: 'passwords/share/:id', component: PasswordShareComponent },  
  { path: '', redirectTo: 'passwords', pathMatch: 'full' }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class PasswordManagerRoutingModule { }