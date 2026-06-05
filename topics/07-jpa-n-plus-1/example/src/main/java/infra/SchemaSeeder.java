package infra;

import domain.Author;
import domain.AuthorRepository;
import domain.Comment;
import domain.CommentRepository;
import domain.Post;
import domain.PostRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Post / Comment 테스트 데이터 삽입. STAGE 2 N+1 시연용. */
@Component
public class SchemaSeeder {

    private final AuthorRepository authorRepo;
    private final PostRepository postRepo;
    private final CommentRepository commentRepo;

    public SchemaSeeder(AuthorRepository authorRepo, PostRepository postRepo, CommentRepository commentRepo) {
        this.authorRepo = authorRepo;
        this.postRepo = postRepo;
        this.commentRepo = commentRepo;
    }

    @Transactional
    public void seed(int postCount, int commentsPerPost) {
        commentRepo.deleteAllInBatch();
        postRepo.deleteAllInBatch();
        authorRepo.deleteAllInBatch();

        Author postAuthor = authorRepo.save(new Author("작성자"));
        Author commentAuthor = authorRepo.save(new Author("댓글러"));

        for (int i = 1; i <= postCount; i++) {
            Post post = postRepo.save(new Post("Post #" + i, postAuthor));
            for (int j = 1; j <= commentsPerPost; j++) {
                commentRepo.save(new Comment("Comment " + i + "-" + j, post, commentAuthor));
            }
        }
        System.out.println("[Seed] post=" + postCount + " commentsPerPost=" + commentsPerPost);
    }
}
