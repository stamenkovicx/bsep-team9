export interface CertificateTemplate {
  id?: number;
  name: string;
  description: string;
  certificateType: 'ROOT' | 'INTERMEDIATE' | 'END_ENTITY';
  
  // Subject Information
  subjectCommonName: string;
  subjectOrganization: string;
  subjectOrganizationalUnit: string;
  subjectCountry: string;
  subjectState: string;
  subjectLocality: string;
  subjectEmail: string;
  
  // Validity
  maxValidityDays: number;
  
  // Extensions
  basicConstraints: boolean;
  keyUsage: {
    digitalSignature: boolean;
    keyEncipherment: boolean;
    keyAgreement: boolean;
    keyCertSign: boolean;
    cRLSign: boolean;
  };
  
  createdDate?: Date;
  lastUsed?: Date;
  usageCount?: number;
}