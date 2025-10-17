import { TestBed } from '@angular/core/testing';

import { CertificateTemplatesService } from './certificate-templates.service';

describe('CertificateTemplatesService', () => {
  let service: CertificateTemplatesService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CertificateTemplatesService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
