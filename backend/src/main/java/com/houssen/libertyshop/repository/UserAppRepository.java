package com.houssen.libertyshop.repository;

import com.houssen.libertyshop.entity.RoleApp;
import com.houssen.libertyshop.entity.UserApp;
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

    List<UserApp> findAllByOrderByFullNameAsc();
}
