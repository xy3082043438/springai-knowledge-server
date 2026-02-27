package com.lamb.springaiknowledgeserver.repository;

import com.lamb.springaiknowledgeserver.entity.Role;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
    boolean existsByName(String name);
    Collection<Role> findByNameIn(Collection<String> names);
}
