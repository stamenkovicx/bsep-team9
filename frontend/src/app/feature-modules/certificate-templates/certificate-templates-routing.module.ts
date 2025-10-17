import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CertificateTemplatesComponent } from './certificate-templates/certificate-templates.component';

const routes: Routes = [
  { path: '', component: CertificateTemplatesComponent }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class CertificateTemplatesRoutingModule { }
