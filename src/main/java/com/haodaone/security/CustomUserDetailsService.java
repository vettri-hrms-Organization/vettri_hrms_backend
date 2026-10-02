package com.haodaone.security;

import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import com.haodaone.user.repository.UserPermissionGrantRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserPermissionGrantRepository permissionGrantRepository;

    public CustomUserDetailsService(UserRepository userRepository, UserPermissionGrantRepository permissionGrantRepository) {
        this.userRepository = userRepository;
        this.permissionGrantRepository = permissionGrantRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameAndDeletedFalse(username)
                .or(() -> userRepository.findByEmailIgnoreCaseAndDeletedFalse(username))
                .orElseThrow(() -> new UsernameNotFoundException("No user found with username: " + username));
        var grants = user.getCompany() == null ? java.util.List.<com.haodaone.user.entity.UserPermissionGrant>of()
                : permissionGrantRepository.findAllByCompany_IdAndUser_IdAndRevokedAtIsNullAndDeletedFalse(
                        user.getCompany().getId(), user.getId());
        return new CustomUserPrincipal(user, grants);
    }
}
