import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CreateCertificateComponent } from './create-certificate/create-certificate.component';

const routes: Routes = [
  { path: 'create', component: CreateCertificateComponent }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class CertificatesRoutingModule { }
