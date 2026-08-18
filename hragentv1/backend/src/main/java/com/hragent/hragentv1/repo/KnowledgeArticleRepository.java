package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.KnowledgeArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, Long> {
    List<KnowledgeArticle> findByTenantIdOrderByUpdatedAtDesc(Long tenantId);

    @Query("""
            select a from KnowledgeArticle a
            where a.tenantId = :tenantId
              and (
                lower(a.title) like lower(concat('%', :keyword, '%'))
                or lower(a.content) like lower(concat('%', :keyword, '%'))
                or lower(a.category) like lower(concat('%', :keyword, '%'))
              )
            order by a.updatedAt desc
            """)
    List<KnowledgeArticle> search(@Param("tenantId") Long tenantId, @Param("keyword") String keyword);
}
