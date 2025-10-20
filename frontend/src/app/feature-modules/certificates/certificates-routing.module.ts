import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CreateCertificateComponent } from './create-certificate/create-certificate.component';
import { ChainViewComponent } from './chain-view/chain-view/chain-view.component';
import { CreateEeCsrComponent } from './create-ee-csr/create-ee-csr.component';

const routes: Routes = [
  { path: 'create', component: CreateCertificateComponent },
  { path: 'issue/csr', component: CreateEeCsrComponent },
  { path: 'view', component: ChainViewComponent }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class CertificatesRoutingModule { }
