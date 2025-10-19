import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { CreateCertificateDTO } from './models/create-certificate.dto';
import { Certificate } from './models/certificate.interface';
import { CreateEeCsrDTO } from '../certificate-templates/models/create-ee-csr.dto';

@Injectable({
  providedIn: 'root'
})
export class CertificateService {
  private apiUrl = 'http://localhost:8089/api/certificates';

  constructor(private http: HttpClient) { }

  createRootCertificate(request: CreateCertificateDTO): Observable<any> {
    return this.http.post(`${this.apiUrl}/root`, request);
  }

  createIntermediateCertificate(request: CreateCertificateDTO): Observable<any> {
    return this.http.post(`${this.apiUrl}/intermediate`, request);
  }

  getIssuers(): Observable<Certificate[]> {
    return this.http.get<Certificate[]>(`${this.apiUrl}/issuers`);
  }

  getAllCertificates(): Observable<Certificate[]> {
    return this.http.get<Certificate[]>(this.apiUrl);
  }

  getRootCertificates(): Observable<Certificate[]> {
    return this.http.get<Certificate[]>(`${this.apiUrl}/root`);
  }

  getMyChain(): Observable<Certificate[]> {
    return this.http.get<Certificate[]>(`${this.apiUrl}/my-chain`);
  }
  getMyChainAsTree(): Observable<Certificate[]> {
    return this.http.get<Certificate[]>(`${this.apiUrl}/my-chain`).pipe(
      map(flatList => {
        // Mapa za brzi pronalazak roditelja
        const map = new Map<number, Certificate>();
        
        // Finalna lista koja sadrži samo Root sertifikate (vrh hijerarhije)
        const tree: Certificate[] = []; 

        // 1. korak: Inicijalizuj mapu i dodaj 'children' polje svakom objektu
        for (const cert of flatList) {
          cert.children = []; // Inicijalizujemo prazno 'children' polje
          map.set(cert.id, cert);
        }

        // 2. korak: Prođi ponovo i poveži decu sa roditeljima
        for (const cert of flatList) {
          if (cert.issuerCertificate != null) {
            // Ovo je "dete". Nađi mu roditelja u mapi.
            const parent = map.get(cert.issuerCertificate.id);
            if (parent && parent.children) {
              // Dodaj ga u 'children' niz njegovog roditelja
              parent.children.push(cert);
            }
          } else {
            // Ovo je Root sertifikat (nema roditelja), dodaj ga u glavnu listu
            tree.push(cert);
          }
        }
        
        // Vrati samo listu Root sertifikata (koji sada u sebi sadrže svu decu)
        return tree;
      })
    );
  }

  downloadCertificate(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/download`, {
      responseType: 'blob'
    });
  }
  revokeCertificate(id: number, reason: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/${id}/revoke`, { reason });
  }
  isCertificateValid(id: number): Observable<{ valid: boolean }> {
    return this.http.get<{ valid: boolean }>(`${this.apiUrl}/${id}/valid`);
  }

  downloadCRL(issuerSerialNumber: string): Observable<Blob> {
    return this.http.get(`http://localhost:8089/api/crl/${issuerSerialNumber}.crl`, {
      responseType: 'blob'
    });
  }

  createEECertificateFromCsr(request: CreateEeCsrDTO): Observable<any> {
    // Ova metoda poziva BE endpoint /api/certificates/end-entity/csr
    return this.http.post(`${this.apiUrl}/end-entity/csr`, request);
}
downloadEECertificate(serialNumber: string): Observable<Blob> {
  return this.http.get(`${this.apiUrl}/end-entity/download/${serialNumber}`, {
   responseType: 'blob'
  });
 }
}