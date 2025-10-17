export interface RevocationReason {
    value: string;
    label: string;
    description: string;
  }
  
  export const REVOCATION_REASONS: RevocationReason[] = [
    { 
      value: 'unspecified', 
      label: 'Unspecified', 
      description: 'Nespecifikovano' 
    },
    { 
      value: 'keyCompromise', 
      label: 'Key Compromise', 
      description: 'Kompromitovan ključ' 
    },
    { 
      value: 'cACompromise', 
      label: 'CA Compromise', 
      description: 'Kompromitovan CA' 
    },
    { 
      value: 'affiliationChanged', 
      label: 'Affiliation Changed', 
      description: 'Promena pripadnosti' 
    },
    { 
      value: 'superseded', 
      label: 'Superseded', 
      description: 'Zamenjen' 
    },
    { 
      value: 'cessationOfOperation', 
      label: 'Cessation of Operation', 
      description: 'Prestanak rada' 
    },
    { 
      value: 'certificateHold', 
      label: 'Certificate Hold', 
      description: 'Privremeno suspendovan' 
    },
    { 
      value: 'privilegeWithdrawn', 
      label: 'Privilege Withdrawn', 
      description: 'Povučene privilegije' 
    }
  ];