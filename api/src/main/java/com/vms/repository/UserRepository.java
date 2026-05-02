
package com.vms.repository;

import com.vms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Boolean existsByUsername(String username);

    Boolean existsByEmail(String email);

    java.util.List<User> findByUserRole(String userRole);

    long countByUserRole(String userRole);

    long countByStatus(com.vms.entity.Status status);

    java.util.List<User> findByStatus(com.vms.entity.Status status);
}
// package com.vms.repository;
//
// import com.vms.entity.User;
// import org.springframework.data.jpa.repository.JpaRepository;
// import java.util.Optional;
// import java.util.UUID;
//
// public interface UserRepository extends JpaRepository<User, UUID> {
// Optional<User> findByUsername(String username);
// Optional<User> findByEmail(String email);
// Boolean existsByUsername(String username);
// Boolean existsByEmail(String email);
// }
