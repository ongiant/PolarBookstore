package com.polarbookshop.edgeservice.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
public class UserController {
    @GetMapping("user")
    public Mono<User> getUser(@AuthenticationPrincipal OidcUser oidcUser){
        User user = new User(
            oidcUser.getPreferredUsername(),
            oidcUser.getGivenName(),
            oidcUser.getFamilyName(),
            List.of("employee", "customer")
        );
        return Mono.just(user);
    }

   /* @GetMapping("user")
    public Mono<User> getUser(){
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(authentication -> (OidcUser) authentication.getPrincipal())
            .map(oidcUser ->
                new User(
                    oidcUser.getPreferredUsername(),
                    oidcUser.getGivenName(),
                    oidcUser.getFamilyName(),
                    List.of("employee", "customer")
                )
            );
    }*/
}
