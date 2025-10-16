import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateCertificateDTO } from './models/create-certificate.dto';
import { Certificate } from './models/certificate.interface';

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

  downloadCertificate(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/download`, {
      responseType: 'blob'
    });
  }
}