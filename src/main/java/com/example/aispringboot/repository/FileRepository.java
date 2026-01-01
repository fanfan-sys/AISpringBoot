package com.example.aispringboot.repository;

import com.example.aispringboot.model.Document;
import com.example.aispringboot.model.File;
import com.example.aispringboot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileRepository extends JpaRepository<File, Long> {
    
    List<File> findByUserOrderByCreatedAtDesc(User user);
    
    List<File> findByDocumentOrderByCreatedAtDesc(Document document);
    
    List<File> findByUserAndDocumentIsNullOrderByCreatedAtDesc(User user);
    
    boolean existsByFileName(String fileName);
}