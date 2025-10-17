// models/certificate.interface.ts
export interface Certificate {
  id: number;
  serialNumber: string;
  subject: string;
  issuer: string;
  validFrom: string;
  validTo: string;
  type: 'ROOT' | 'INTERMEDIATE' | 'END_ENTITY';
  status: 'VALID' | 'REVOKED' | 'EXPIRED';
  
  // CA properties
  isCA?: boolean;
  basicConstraints?: string;
  keyUsage?: string;
  extendedKeyUsage?: string;
  pathLengthConstraint?: string;
  
  // ✅ REVOCATION FIELDS (NOVO)
  revocationReason?: string;
  revokedAt?: string;
  
  // ✅ CRL FIELDS (NOVO)
  crlNumber?: number;
  lastCRLUpdate?: string;
  
  // Owner information
  owner?: {
    id: number;
    name?: string;
    email: string;
    organization?: string;
    role?: string;
  };
  
  // Issuer certificate reference
  issuerCertificate?: Certificate;
  
  // Parsed subject fields (optional - za lakši prikaz)
  subjectCommonName?: string;
  subjectOrganization?: string;
  subjectOrganizationalUnit?: string;
  subjectCountry?: string;
  subjectState?: string;
  subjectLocality?: string;
  subjectEmail?: string;
  
  // Public key
  publicKey?: string;
}