package domain;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {

    // STAGE 2-2 — JPQL JOIN FETCH
    @Query("select distinct p from Post p join fetch p.comments")
    List<Post> findAllWithCommentsJoinFetch();

    // STAGE 2-3 — @EntityGraph (선언형 — JPQL 없이 derived query 에 얹음)
    // 주의: @EntityGraph + @Query 조합은 Hibernate 6 / Spring Data 3 에서 의도와 다르게 동작 가능.
    // 정석은 derived query 또는 findAll 오버라이드 한 줄.
    @EntityGraph(attributePaths = {"comments"})
    List<Post> findAllByOrderByIdAsc();

    // STAGE 2-5 — fetch join + 페이징 한계 (HHH000104 WARN 재현)
    @Query("select distinct p from Post p join fetch p.comments")
    List<Post> findAllWithCommentsPaged(Pageable pageable);
}
