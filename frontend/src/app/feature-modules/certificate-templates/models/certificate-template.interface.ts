export interface CertificateTemplate {
  id?: number;
  name: string;
  description: string;
  caIssuerId: number;
  caIssuerName?: string;
  commonNameRegex?: string;
  sansRegex?: string;
  maxValidityDays: number;
  keyUsage: boolean[];
  extendedKeyUsage: string;
  basicConstraints: string;
  createdAt?: Date;
  createdBy?: string;
  usageCount?: number;
}