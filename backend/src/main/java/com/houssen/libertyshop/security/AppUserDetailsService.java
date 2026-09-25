package com.houssen.libertyshop.security;

import com.houssen.libertyshop.repository.UserAppRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserAppRepository users;

    public AppUserDetailsService(UserAppRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByUsername(username)
                .map(AppUserDetails::new)
                // Deliberately vague: the caller must not learn whether the name exists.
                .orElseThrow(() -> new UsernameNotFoundException("Identifiants invalides."));
    }
}
