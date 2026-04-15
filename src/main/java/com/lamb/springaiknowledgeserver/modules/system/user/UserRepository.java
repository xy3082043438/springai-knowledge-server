package com.lamb.springaiknowledgeserver.modules.system.user;

import com.lamb.springaiknowledgeserver.modules.system.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    long countByRole_Id(Long roleId);
}


