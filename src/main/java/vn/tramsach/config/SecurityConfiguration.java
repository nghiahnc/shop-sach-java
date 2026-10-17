package vn.tramsach.config;

import org.springframework.context.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
@Profile("!probe")
public class SecurityConfiguration {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  @Order(1)
  SecurityFilterChain publicStorefront(HttpSecurity http) throws Exception {
    return http.securityMatcher(r -> StorefrontRoutes.publicPage(r) || StorefrontRoutes.asset(r))
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .securityContext(c -> c.securityContextRepository(new NullSecurityContextRepository()))
        .requestCache(c -> c.requestCache(new NullRequestCache()))
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(c -> c.disable())
        .headers(
            h ->
                h.cacheControl(c -> c.disable())
                    .contentSecurityPolicy(
                        c ->
                            c.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; style-src 'self';"
                                    + " font-src 'self'; script-src 'self'; form-action 'self'"
                                    + " https://checkout.stripe.com; frame-ancestors 'none';"
                                    + " base-uri 'self'")))
        .build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            a ->
                a.requestMatchers("/admin", "/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/checkout", "/checkout/**", "/account/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .formLogin(
            f -> f.loginPage("/login").defaultSuccessUrl("/", false).failureUrl("/login?error"))
        .logout(l -> l.logoutSuccessUrl("/"))
        .csrf(c -> c.ignoringRequestMatchers("/payments/stripe/webhook"))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; img-src 'self' data:; style-src 'self'; font-src"
                                + " 'self'; script-src 'self'; form-action 'self'"
                                + " https://checkout.stripe.com; frame-ancestors 'none'; base-uri"
                                + " 'self'")))
        .build();
  }
}
