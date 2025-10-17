export interface CreateTemplateDTO {
  name: string;
  description: string;
  caIssuerId: number;
  commonNameRegex?: string;
  sansRegex?: string;
  maxValidityDays: number;
  keyUsage: boolean[];
  extendedKeyUsage: string;
  basicConstraints: string;
}