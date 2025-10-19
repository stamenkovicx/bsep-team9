import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { ACCESS_TOKEN } from '../../../shared/constants';

@Injectable()
export class JwtInterceptor implements HttpInterceptor {
  constructor() {}

  intercept(
    request: HttpRequest<any>,
    next: HttpHandler
  ): Observable<HttpEvent<any>> {
    // Ako već postoji Authorization header, NE MIJENJAJ GA
    if (request.headers.has('Authorization')) {
      return next.handle(request);
    }

    // Inače dodaj token iz localStorage
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