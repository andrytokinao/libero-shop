package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.UserPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPhotoRepository extends JpaRepository<UserPhoto, Long> {
}
