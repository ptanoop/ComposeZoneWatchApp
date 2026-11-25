package com.zonewatch.repository;

import com.zonewatch.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByUsername(String username);

    @Modifying
    @Query(value = """
            INSERT INTO users (username, password_hash, role)
            VALUES (:username, :passwordHash, :role)
            ON CONFLICT (username) DO UPDATE
            SET password_hash = EXCLUDED.password_hash, role = EXCLUDED.role
            """, nativeQuery = true)
    void upsertUser(@Param("username") String username,
                    @Param("passwordHash") String passwordHash,
                    @Param("role") String role);
}
