export interface CreateEeCsrDTO {
  csrPem: string;
  
  validTo: string; // Očekuje se YYYY-MM-DD format na backendu
  issuerCertificateId: number;
}