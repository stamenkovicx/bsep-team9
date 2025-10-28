package com.bsep.pki_system.jwt;

import com.bsep.pki_system.model.UserRole;
import com.bsep.pki_system.service.TokenSessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenSessionService tokenSessionService;

    public JwtAuthFilter(JwtService jwtService, TokenSessionService tokenSessionService) {
        this.jwtService = jwtService;
        this.tokenSessionService = tokenSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        // 1. Provera da li je token privremen
        try {
            Claims claims = jwtService.getClaims(token);
            Boolean temporary = claims.get("temporary", Boolean.class);
            if (temporary != null && temporary) {
                // Preskoči standardnu autentifikaciju
                filterChain.doFilter(request, response);
                return;
            }
        } catch (Exception e) {
            // Ako token nije validan, samo nastavi dalje, filter će ga ignorisati
            filterChain.doFilter(request, response);
            return;
        }

        if (jwtService.validateToken(token)) {
            Claims claims = jwtService.getClaims(token);
            String sessionId = claims.getId(); // Get session ID from jti claim
            
            // Check if session is revoked
            // When a session is revoked, explicitly deny access with 401 Unauthorized
            if (sessionId != null && tokenSessionService.isSessionRevoked(sessionId)) {
                // Session is revoked - return 401 to immediately log out the user
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Your session has been revoked\"}");
                return;
            }
            
            // Update last activity if session is active
            if (sessionId != null) {
                tokenSessionService.updateLastActivity(sessionId);
            }
            
            String email = claims.getSubject();
            Long userId = claims.get("userId", Long.class);
            UserRole role = UserRole.valueOf(claims.get("role", String.class));

            UserPrincipal principal = new UserPrincipal(userId, email, role);

            List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                    new SimpleGrantedAuthority("ROLE_" + role.name())
            );

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
