package com.example.aispringboot.controller;

import com.example.aispringboot.entity.Document;
import com.example.aispringboot.entity.User;
import com.example.aispringboot.payload.response.MessageResponse;
import com.example.aispringboot.repository.DocumentRepository;
import com.example.aispringboot.security.services.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @Autowired
    private DocumentRepository documentRepository;

    @GetMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<Document>> getMyDocuments() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = new User();
        user.setId(userDetails.getId());
        
        List<Document> documents = documentRepository.findByUserAndIsDeletedFalseOrderByUpdatedAtDesc(user);
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/public")
    public ResponseEntity<List<Document>> getPublicDocuments() {
        List<Document> documents = documentRepository.findByIsPublicTrueAndIsDeletedFalseOrderByUpdatedAtDesc();
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDocument(@PathVariable Long id) {
        Optional<Document> documentOpt = documentRepository.findByIdAndIsDeletedFalse(id);
        
        if (documentOpt.isPresent()) {
            Document document = documentOpt.get();
            
            // 检查权限：公开文档或自己的文档
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (document.getIsPublic() || 
                (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl &&
                 ((UserDetailsImpl) authentication.getPrincipal()).getId().equals(document.getUser().getId()))) {
                
                // 增加浏览次数
                document.setViewCount(document.getViewCount() + 1);
                documentRepository.save(document);
                
                return ResponseEntity.ok(document);
            } else {
                return ResponseEntity.status(403).body(new MessageResponse("Access denied"));
            }
        }
        
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> createDocument(@RequestBody Document documentRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = new User();
        user.setId(userDetails.getId());
        
        Document document = new Document();
        document.setTitle(documentRequest.getTitle());
        document.setContent(documentRequest.getContent());
        document.setUser(user);
        document.setIsPublic(documentRequest.getIsPublic() != null ? documentRequest.getIsPublic() : false);
        
        Document savedDocument = documentRepository.save(document);
        return ResponseEntity.ok(savedDocument);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> updateDocument(@PathVariable Long id, @RequestBody Document documentRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        
        Optional<Document> documentOpt = documentRepository.findByIdAndIsDeletedFalse(id);
        
        if (documentOpt.isPresent()) {
            Document document = documentOpt.get();
            
            // 检查是否是文档所有者
            if (!document.getUser().getId().equals(userDetails.getId())) {
                return ResponseEntity.status(403).body(new MessageResponse("Access denied"));
            }
            
            document.setTitle(documentRequest.getTitle());
            document.setContent(documentRequest.getContent());
            document.setIsPublic(documentRequest.getIsPublic());
            document.setUpdatedAt(LocalDateTime.now());
            
            Document updatedDocument = documentRepository.save(document);
            return ResponseEntity.ok(updatedDocument);
        }
        
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteDocument(@PathVariable Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        
        Optional<Document> documentOpt = documentRepository.findByIdAndIsDeletedFalse(id);
        
        if (documentOpt.isPresent()) {
            Document document = documentOpt.get();
            
            // 检查是否是文档所有者
            if (!document.getUser().getId().equals(userDetails.getId())) {
                return ResponseEntity.status(403).body(new MessageResponse("Access denied"));
            }
            
            document.setIsDeleted(true);
            documentRepository.save(document);
            
            return ResponseEntity.ok(new MessageResponse("Document deleted successfully"));
        }
        
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<Document>> searchDocuments(@RequestParam String keyword) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        List<Document> documents;
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            User user = new User();
            user.setId(userDetails.getId());
            documents = documentRepository.searchByUserAndTitle(user, keyword);
        } else {
            documents = documentRepository.searchPublicByTitle(keyword);
        }
        
        return ResponseEntity.ok(documents);
    }
}