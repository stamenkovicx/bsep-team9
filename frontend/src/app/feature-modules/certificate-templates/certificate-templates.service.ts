import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CertificateTemplate } from './models/certificate-template.interface';
import { CreateTemplateDTO } from './models/create-template.dto';

@Injectable({
  providedIn: 'root'
})
export class CertificateTemplatesService {
  private apiUrl = 'http://localhost:8089/api/templates';

  constructor(private http: HttpClient) { }

  getAllTemplates(): Observable<CertificateTemplate[]> {
    return this.http.get<CertificateTemplate[]>(this.apiUrl);
  }

  getTemplateById(id: number): Observable<CertificateTemplate> {
    return this.http.get<CertificateTemplate>(`${this.apiUrl}/${id}`);
  }

  createTemplate(template: CreateTemplateDTO): Observable<CertificateTemplate> {
    return this.http.post<CertificateTemplate>(this.apiUrl, template);
  }

  updateTemplate(id: number, template: CertificateTemplate): Observable<CertificateTemplate> {
    return this.http.put<CertificateTemplate>(`${this.apiUrl}/${id}`, template);
  }

  deleteTemplate(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  useTemplate(templateId: number): Observable<any> {
    return this.http.post(`${this.apiUrl}/${templateId}/use`, {});
  }
}