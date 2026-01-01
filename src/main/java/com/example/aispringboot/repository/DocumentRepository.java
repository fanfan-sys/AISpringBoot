package com.example.aispringboot.repository;

import com.example.aispringboot.entity.Document;
import com.example.aispringboot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUserAndIsDeletedFalseOrderByUpdatedAtDesc(User user);
    
    List<Document> findByIsPublicTrueAndIsDeletedFalseOrderByUpdatedAtDesc();
    
    Optional<Document> findByIdAndIsDeletedFalse(Long id);
    
    @Query("SELECT d FROM Document d WHERE d.user = :user AND d.title LIKE %:keyword% AND d.isDeleted = false")
    List<Document> searchByUserAndTitle(@Param("user") User user, @Param("keyword") String keyword);
    
    @Query("SELECT d FROM Document d WHERE d.isPublic = true AND d.title LIKE %:keyword% AND d.isDeleted = false")
    List<Document> searchPublicByTitle(@Param("keyword") String keyword);
}