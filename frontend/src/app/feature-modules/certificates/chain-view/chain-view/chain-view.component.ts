// U 'src/app/feature-modules/certificates/chain-view/chain-view/chain-view.component.ts'

import { Component, OnInit } from '@angular/core';
import { CertificateService } from '../../certificate.service';
import { Certificate } from '../../models/certificate.interface';
import { MatTreeFlatDataSource, MatTreeFlattener } from '@angular/material/tree';
import { FlatTreeControl } from '@angular/cdk/tree';

// Definisemo "ravni" čvor (flat node) koji MatTree koristi
interface CertificateFlatNode {
  expandable: boolean;
  name: string; // Ovo je ono sto ce se prikazati
  level: number;
  data: Certificate; // Originalni podaci
}

@Component({
  selector: 'xp-chain-view',
  templateUrl: './chain-view.component.html',
  styleUrls: ['./chain-view.component.css'] 
})
export class ChainViewComponent implements OnInit {

  private _transformer = (node: Certificate, level: number) => {
    
    let displayName = node.subjectCommonName; // 1. Probaj parsirano polje (ako ga imaš)

    if (!displayName) { // 2. Ako ne postoji, izvuci ga ručno iz 'subject' stringa
      try {
        // Traži 'CN=' u stringu
        const match = node.subject.match(/CN=([^,]+)/);
        if (match && match[1]) {
          displayName = match[1]; // Uzima 'Root TRECI ZA High School'
        } else {
          displayName = node.subject;
        }
      } catch (e) {
        displayName = node.subject;
      }
    }

    return {
      expandable: !!node.children && node.children.length > 0,
      name: `${displayName} (${node.type})`, // Koristimo 'displayName'
      level: level,
      data: node
    };
  }

  treeControl = new FlatTreeControl<CertificateFlatNode>(
    node => node.level, 
    node => node.expandable
  );

  treeFlattener = new MatTreeFlattener(
    this._transformer, 
    node => node.level, 
    node => node.expandable, 
    node => node.children
  );

  dataSource = new MatTreeFlatDataSource(this.treeControl, this.treeFlattener);

  constructor(private certificateService: CertificateService) {}

  ngOnInit(): void {
    this.certificateService.getMyChainAsTree().subscribe({
      next: (treeData) => {
        this.dataSource.data = treeData;
      },
      error: (err) => {
        console.error('Greška pri učitavanju lanca:', err);
      }
    });
  }

  hasChild = (_: number, node: CertificateFlatNode) => node.expandable;
}