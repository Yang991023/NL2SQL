package com.nl2sql.auth.repo;

import com.nl2sql.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndIdNot(String username, Long id);

    @Query("SELECT u FROM User u WHERE " +
           "(:kw IS NULL OR u.username LIKE %:kw% OR u.nickname LIKE %:kw%)")
    Page<User> findPage(@Param("kw") String keyword, Pageable pageable);
}
