package com.houssen.libertyshop.repository;

import com.houssen.libertyshop.entity.RoleApp;
import com.houssen.libertyshop.entity.UserApp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAppRepository extends JpaRepository<UserApp, Long> {

    Optional<UserApp> findByUsername(String username);

    boolean existsByUsername(String username);

    List<UserApp> findByRoleOrderByFullNameAsc(RoleApp role);

    List<UserApp> findAllByOrderByFullNameAsc();
}
