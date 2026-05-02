package com.vms.config;

import com.vms.entity.Role;
import com.vms.entity.RoleName;
import com.vms.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    @Override
    public void run(String... args) throws Exception {
        if (roleRepository.count() == 0) {
            roleRepository.save(new Role(null, RoleName.ADMIN));
            roleRepository.save(new Role(null, RoleName.FINANCE));
            roleRepository.save(new Role(null, RoleName.USER));
            roleRepository.save(new Role(null, RoleName.VENDOR));
            System.out.println("Roles initialized in database.");
        }
    }
}
