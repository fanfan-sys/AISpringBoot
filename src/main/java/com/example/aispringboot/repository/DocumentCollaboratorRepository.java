package com.example.aispringboot.repository;

import com.example.aispringboot.model.Document;
import com.example.aispringboot.model.DocumentCollaborator;
import com.example.aispringboot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentCollaboratorRepository extends JpaRepository<DocumentCollaborator, Long> {
    
    List<DocumentCollaborator> findByDocumentAndIsActiveTrue(Document document);
    
    List<DocumentCollaborator> findByDocument(Document document);
    
    Optional<DocumentCollaborator> findByDocumentAndUser(Document document, User user);
    
    boolean existsByDocumentAndUserAndIsActiveTrue(Document document, User user);
}