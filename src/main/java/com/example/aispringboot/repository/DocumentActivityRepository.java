package com.example.aispringboot.repository;

import com.example.aispringboot.model.Document;
import com.example.aispringboot.model.DocumentActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentActivityRepository extends JpaRepository<DocumentActivity, Long> {
    
    List<DocumentActivity> findByDocumentOrderByCreatedAtDesc(Document document);
    
    List<DocumentActivity> findTop10ByDocumentOrderByCreatedAtDesc(Document document);
}