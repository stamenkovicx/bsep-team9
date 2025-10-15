export interface CreateCertificateDTO {
  subjectCommonName: string;
  subjectOrganization: string;
  subjectOrganizationalUnit: string;
  subjectCountry: string;
  subjectState: string;
  subjectLocality: string;
  subjectEmail: string;
  validFrom: string;
  validTo: string;
  keyUsage: boolean[];
  basicConstraints: string;
  extendedKeyUsage: string;
  issuerCertificateId: number | null;
}