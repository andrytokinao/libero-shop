package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAppRepository extends JpaRepository<UserApp, Long> {

    Optional<UserApp> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Accounts that may do this job, whether or not they also do another one. */
    @Query("""
            select u from UserApp u
            where :role member of u.roles
            order by u.fullName asc
            """)
    List<UserApp> findByRoleOrderByFullNameAsc(@Param("role") RoleApp role);

    /**
     * How many accounts hold this job and can still sign in.
     *
     * <p>Asked before a role is taken away or an account is switched off, so the last
     * super-admin cannot be removed and lock the shop out of its own administration.
     */
    @Query("""
            select count(u) from UserApp u
            where :role member of u.roles and u.enabled = true
            """)
    long countEnabledByRole(@Param("role") RoleApp role);

    List<UserApp> findAllByOrderByFullNameAsc();
}
