package domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    // @ManyToOne 기본 EAGER → 실무는 LAZY 명시 권장
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Author author;

    // @OneToMany 기본 LAZY — N+1 의 원천. Stage2_1 에서 그대로 재현.
    // Stage2_4 의 @BatchSize 시연 — 두 경로 중 선택:
    //   (A) 필드 어노테이션: 아래 @BatchSize 주석 해제
    //   (B) 전역 프로퍼티: application.properties 의 hibernate.default_batch_fetch_size 주석 해제
    // @org.hibernate.annotations.BatchSize(size = 100)
    @OneToMany(mappedBy = "post")
    private List<Comment> comments = new ArrayList<>();

    protected Post() {}

    public Post(String title, Author author) {
        this.title = title;
        this.author = author;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Author getAuthor() { return author; }
    public List<Comment> getComments() { return comments; }
}
