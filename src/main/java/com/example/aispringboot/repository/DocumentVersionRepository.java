package com.example.aispringboot.repository;

import com.example.aispringboot.model.Document;
import com.example.aispringboot.model.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
    
    List<DocumentVersion> findByDocumentOrderByVersionNumberDesc(Document document);
    
    DocumentVersion findTopByDocumentOrderByVersionNumberDesc(Document document);
    
    int countByDocument(Document document);
}