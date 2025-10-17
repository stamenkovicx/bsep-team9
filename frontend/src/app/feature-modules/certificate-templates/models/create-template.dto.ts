export interface CreateTemplateDTO {
  name: string;
  description: string;
  caIssuerId?: number;
  certificateType: 'ROOT' | 'INTERMEDIATE' | 'END_ENTITY';
  subjectCommonName: string;
  subjectOrganization: string;
  subjectOrganizationalUnit: string;
  subjectCountry: string;
  subjectState: string;
  subjectLocality: string;
  subjectEmail: string;
  maxValidityDays: number;
  basicConstraints: boolean;
  keyUsage: boolean[];
}