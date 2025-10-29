import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable, from } from "rxjs";
import { ACCESS_TOKEN } from '../../../shared/constants';
import { KeycloakService } from '../keycloak.service';
import { mergeMap } from 'rxjs/operators';

@Injectable()
export class JwtInterceptor implements HttpInterceptor {
  constructor(private keycloakService: KeycloakService) {}

  intercept(
    request: HttpRequest<any>,
    next: HttpHandler
  ): Observable<HttpEvent<any>> {
    // Ako već postoji Authorization header, NE MIJENJAJ GA
    if (request.headers.has('Authorization')) {
      return next.handle(request);
    }

    // Try to get token from Keycloak first
    const keycloakToken = this.keycloakService.getToken();
    if (keycloakToken) {
      const accessTokenRequest = request.clone({
        setHeaders: {
          Authorization: `Bearer ${keycloakToken}`,
        },
      });
      return next.handle(accessTokenRequest);
    }

    // Fallback to localStorage token
    const token = localStorage.getItem(ACCESS_TOKEN);
    if (token) {
      const accessTokenRequest = request.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`,
        },
      });
      return next.handle(accessTokenRequest);
    }

    return next.handle(request);
  }
}