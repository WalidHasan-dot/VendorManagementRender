package com.vms.security;

import com.vms.entity.User;
import com.vms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Autowired
    UserRepository userRepository;

    @Autowired
    com.vms.repository.VendorRepository vendorRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + username));

        com.vms.entity.Vendor vendor = vendorRepository.findByUser(user).orElse(null);
        java.util.UUID vendorId = vendor != null ? vendor.getId() : null;

        return UserDetailsImpl.build(user, vendorId);
    }
}
