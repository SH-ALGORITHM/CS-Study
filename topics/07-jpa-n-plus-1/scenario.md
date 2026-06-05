# 7주차 — 내가 SQL 안 쓴 INSERT/UPDATE가 자동으로 나가는 이유 (JPA 영속성 컨텍스트 + N+1)

이번 주제: 1 ~ 3 주차 내내 직접 `conn.prepareStatement("UPDATE ...")` 짠 코드를 4 주차에 JdbcTemplate 으로 감쌌고, 5 ~ 6 주차에서는 Spring 의 메서드 호출 / 이벤트 메커니즘 위에 얹었다. 7 주차는 그 **SQL 자체를 내가 안 쓰는** 세계로 들어간다. `setName("바뀐 이름")` 한 줄만 했는데 UPDATE 가 자동 발행되고, `persist(post)` 직후엔 INSERT 가 안 나가다가 commit 시점에 한꺼번에 나가는 그 "영속성 컨텍스트" 의 메커니즘. 그리고 그 마법의 대가 — N+1 문제를 직접 재현하고 4 가지로 해결한다.

5 가지 학습 축:
- **영속성 컨텍스트 4 마법** — 1 차 캐시 / 변경 감지 (dirty checking) / 쓰기 지연 (write-behind) / 동일성 보장. 모두 1 트랜잭션 안에서만
- **`@Transactional` 과 영속성 컨텍스트의 결합** — 트랜잭션 = 컨텍스트 수명. 5 주차 ThreadLocal `TransactionSynchronizationManager` 가 영속성 컨텍스트도 함께 관리
- **Lazy 로딩 + `LazyInitializationException`** — 트랜잭션 밖 / 새 스레드 (`@Async` + AFTER_COMMIT) 에서 Lazy 접근 = 폭발. 6 주차 함정 회수
- **N+1 문제** ★ — 게시글 N 개 조회 + 각 게시글의 댓글 N 번 조회 = 1+N 쿼리. 재현 + 해결 4 가지 + fetch join 한계
- **`@DomainEvents`** — Aggregate Root 가 publisher 없이 이벤트 발행. 6 주차 회수 + 도메인 객체가 Spring 의존성 없이 순수

---

## 우선 알아둬야 할 단어 (시작 전 1 분)

| 단어 | 풀어쓰면 |
|---|---|
| **JPA** (Jakarta Persistence API) | 자바 표준 ORM 명세. Hibernate 가 가장 흔한 구현체 |
| **Hibernate** | JPA 의 사실상 표준 구현. 영속성 컨텍스트 / 1 차 캐시 / 변경 감지 등 마법 |
| **Entity** | `@Entity` 붙은 클래스. DB 테이블 한 행과 매핑. 식별자 (PK) 필수 |
| **EntityManager** | JPA 의 핵심 인터페이스. `persist` / `find` / `merge` / `remove`. 영속성 컨텍스트 관리 |
| **영속성 컨텍스트** (Persistence Context) | EntityManager 가 관리하는 "Entity 의 1 차 캐시 + 상태 추적기". 트랜잭션 수명 |
| **1 차 캐시** | 같은 트랜잭션 안에서 같은 id 두 번 `findById` = SELECT 한 번만 |
| **변경 감지** (Dirty Checking) | `entity.setName(...)` 만 했는데 UPDATE 자동 발행. flush 시점에 스냅샷 비교 |
| **쓰기 지연** (Write-behind) | `persist(entity)` 직후 INSERT 안 나감. flush 시점에 모아서 발행 → 배치 최적화 가능 |
| **flush** | 영속성 컨텍스트의 변경 사항을 DB 에 SQL 로 동기화. `em.flush()` 직접 호출 / commit 직전 자동 |
| **fetch=LAZY** | 연관 객체 (`@OneToMany` / `@ManyToOne`) 를 접근 시점에 SELECT. 기본 (`@OneToMany` / `@ManyToMany`) |
| **fetch=EAGER** | 연관 객체를 처음 SELECT 시 함께 가져옴. 기본 (`@ManyToOne` / `@OneToOne`) |
| **`LazyInitializationException`** | 영속성 컨텍스트 밖에서 Lazy 컬렉션 / 객체 접근 시 폭발 |
| **N+1 문제** | 게시글 N 개 SELECT 1 회 + 각 게시글의 Lazy 컬렉션 접근 시 SELECT N 회 = 1+N 쿼리 |
| **JOIN FETCH** | JPQL — `select p from Post p join fetch p.comments` — 한 쿼리로 가져오기 |
| **`@EntityGraph`** | Spring Data — JPQL 안 짜고 선언형으로 JOIN FETCH |
| **OSIV** (Open Session In View) | Spring Boot 기본 ON — 컨트롤러 / View 까지 영속성 컨텍스트 유지. 함정 |

> 📚 더 깊은 용어 (4 마법 각각 / fetch join 한계 / @BatchSize / cascading / orphanRemoval 등) — [`terms.md`](terms.md) 참고. 6 주차와 같은 형식, 카테고리별 정리.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 0. 개념 숙지 (도메인 무관, 모두 동일)
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 필수 개념 (AI 와 대화하며 숙지)

### 영속성 컨텍스트가 풀려는 문제 (1 ~ 3 주차 회상)
1. **JdbcTemplate 시절의 불편** — `jdbc.update("UPDATE account SET balance = ? WHERE id = ?", b, id)` 같은 SQL 을 도메인 객체 변경마다 직접 호출해야. 게시글 제목 / 본문 / 작성자 / 수정일을 한 번에 바꾸려면 UPDATE 컬럼 다 나열
2. **영속성 컨텍스트의 약속** — "Entity 객체를 자바 코드로만 다뤄라. SQL 은 내가 알아서 생성한다". 핵심 — `entity.setName(...)` 한 줄 → flush 시점에 변경 감지 → UPDATE 자동
3. **대가** — 컨텍스트가 살아있는 동안 모든 Entity 의 스냅샷을 들고 있어야. N+1 문제 / Lazy 함정 / OSIV 메모리 누수 등 7 주차 학습 본질

### 영속성 컨텍스트 4 마법
4. **1 차 캐시** — 같은 EntityManager 안에서 `findById(1L)` 두 번 → 첫 번째만 SELECT, 두 번째는 캐시 반환. 같은 객체 (`==` true)
5. **변경 감지** (Dirty Checking) — 영속 상태 Entity 의 setter 호출만 해도 flush 시점에 **스냅샷과 비교** → 변경된 컬럼만 UPDATE
6. **쓰기 지연** — `persist(entity)` 직후엔 SQL 큐에 쌓이기만. flush 시점에 한꺼번에 발행 → JDBC batch / 순서 최적화 가능
7. **동일성 보장** — 같은 트랜잭션 안에서 같은 id 의 Entity = 같은 인스턴스. `findById(1L) == findById(1L)` (`==` true)

### EntityManager 스코프 + `@Transactional` 결합
8. **트랜잭션 = 영속성 컨텍스트 수명** — `@Transactional` 메서드 진입 시 EntityManager 생성, 종료 시 close. 5 주차 `TransactionSynchronizationManager` 가 둘 다 관리 — 같은 ThreadLocal
9. **`@Transactional` 밖에서 Repository 호출** — Spring Data JPA 의 `SimpleJpaRepository` 가 클래스 레벨 `@Transactional` 보유. **조회 메서드 (`findById` 등) 는 `readOnly=true`** / **쓰기 메서드 (`save`, `delete`) 는 일반 트랜잭션**. 단 같은 호출 안에서 영속성 컨텍스트는 그 메서드만 살아있음 → 반환 후 Lazy 접근 시 폭발
10. **`@Transactional(readOnly = true)`** — Hibernate 가 변경 감지 스냅샷을 안 만듦 + flush 모드 MANUAL. 조회 전용 메서드에 명시 시 성능 + 안전

### N+1 문제 (★ 핵심)
11. **N+1 재현** — `posts = postRepo.findAll()` → SELECT * FROM post (1 회). `for (Post p : posts) p.getComments().size()` → 게시글마다 SELECT * FROM comment WHERE post_id=? (N 회). **총 1+N 쿼리**
12. **`@OneToMany` 기본 fetch=LAZY** — Comment 컬렉션이 Lazy. 접근 시점에야 SELECT. 의도는 좋으나 N 번 반복하면 폭발
13. **해결 4 가지** —
    - (a) **JPQL JOIN FETCH** — `select p from Post p join fetch p.comments`. 한 쿼리
    - (b) **`@EntityGraph`** — Spring Data 메서드에 어노테이션. JPQL 안 짜고 선언형
    - (c) **`@BatchSize(N)`** — 컬렉션 / 연관 단위로 IN 절 묶음. `WHERE post_id IN (?, ?, ...)` 한 번
    - (d) **`hibernate.default_batch_fetch_size`** — 전역 batch size. (c) 의 자동화
14. **fetch join 의 한계** — (a) 1:N 컬렉션 + 페이징 동시 = 메모리에서 페이징 (WARN 로그) + 데이터 중복 / (b) 1:N 컬렉션 2 개 동시 fetch = `MultipleBagFetchException`. 해결 = `@BatchSize` 와 조합

### Lazy 로딩 함정
15. **`LazyInitializationException`** 발생 조건 — 영속성 컨텍스트가 close 된 상태에서 Lazy 컬렉션 / 객체 접근. 컨트롤러 / View / Async / commit 후 리스너 등
16. **6 주차 회수** — `@TransactionalEventListener(AFTER_COMMIT) + @Async` 리스너에서 Entity 의 Lazy 컬렉션 접근 → 본 트랜잭션 끝 + 새 스레드 = 영속성 컨텍스트 없음 → 폭발. 해결 = `@Transactional(REQUIRES_NEW)` + 이벤트 payload 에 필요한 데이터 미리 포함
17. **OSIV** (Open Session In View) — Spring Boot 기본 `spring.jpa.open-in-view=true`. **단 OSIV 의 Lazy 보호는 서블릿 요청 컨텍스트 안에서만 동작** (DispatcherServlet → OSIV 인터셉터가 세션 열고 응답 끝까지 유지). `main` 직접 호출 / `@Async` 별 스레드 / 배치 작업은 OSIV 와 무관하게 폭발. 웹 요청 안에서도 응답 끝까지 DB 커넥션 점유 → 트래픽 많으면 커넥션 풀 고갈

### `@DomainEvents` (6 주차 회수)
18. **`AbstractAggregateRoot<T>`** — Spring Data JPA 가 제공하는 Aggregate Root base. `registerEvent(event)` 한 줄로 이벤트 큐에 등록 → `repository.save(entity)` 호출 시 자동 publishEvent
19. **6 주차 publishEvent 와 차이** — publisher 주입 불필요. 도메인 객체가 Spring 의존성 없이 순수 (DDD 친화). 단 `repository.save` 거쳐야 발행되므로 호출 흐름 명시 필요

## 자기 검증 (입으로 답할 수 있어야 STAGE 1 시작)

**★ 관문 — 이 3 개는 입으로 답해야 STAGE 1 진입**
- [ ] ★ 영속성 컨텍스트 4 마법 각각 1 줄 — 1 차 캐시 / 변경 감지 / 쓰기 지연 / 동일성 보장
- [ ] ★ N+1 문제가 왜 발생하는가 + 해결 4 가지 중 본인이 가장 자주 쓸 1 가지
- [ ] ★ `LazyInitializationException` 발생 조건 + 6 주차 `@Async` 함정과의 연결

**보너스 — STAGE 1 진행하면서 답할 수 있게**
- [ ] `@Transactional` 메서드 진입 / 종료 시 영속성 컨텍스트는 언제 생기고 사라지나
- [ ] 변경 감지가 flush 시점에 동작하는 메커니즘 — 스냅샷이란?
- [ ] `findById(1L) == findById(1L)` 가 true 인 이유
- [ ] fetch=LAZY 와 EAGER 의 기본값 — `@OneToMany` / `@ManyToOne` 각각
- [ ] JOIN FETCH 와 `@EntityGraph` 차이 — 어느 쪽이 더 권장
- [ ] fetch join + 페이징 동시에 쓰면 발생하는 WARN 메시지 + 이유
- [ ] `MultipleBagFetchException` 발생 조건 + 해결
- [ ] OSIV 의 트레이드오프 — 켜고 끄는 기준
- [ ] `@Transactional(readOnly = true)` 가 변경 감지 / 메모리에 미치는 영향
- [ ] 6 주차 `@DomainEvents` 가 7 주차에서 어떻게 동작하나 (`AbstractAggregateRoot`)


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 1. 본인 도메인 선택 — 7 주차에 맞게 (1:N 자연 + N+1 재현 가능)
━━━━━━━━━━━━━━━━━━━━━━━━━━

7 주차 학습 포인트 (**영속성 컨텍스트 4 마법 / N+1 / Lazy 함정**) 는 **1:N 또는 M:N 연관이 자연스럽고 컬렉션 순회가 일상인 도메인** 에서 잘 드러난다. 단일 Entity 도메인 (예: 6 주차 송금 단건) 은 N+1 자체가 안 만들어짐.

## 옵션 — 6 주차 도메인 그대로 vs 새 도메인

| 옵션 | 권장 대상 | 흐름 |
|---|---|---|
| **A. 6 주차 도메인 그대로 + 연관 추가** | 도메인 새로 짜기 부담스러운 사람 | 6 주차 주문 / 결제 / 회원가입에 1:N 관계 추가 (Order-OrderItem / Payment-Receipt / User-Profile 등) |
| **B. 새 도메인 선택** | 7 주차 학습 본격 | STEP 1 후보표에서 N+1 재현 ★★★ 도메인 (게시판 / 강의 / 도서관 등) 선택 |
| **C. 혼합** | 가장 무난 | STAGE 1 (영속성 컨텍스트 4 마법) 까지 공통 학습 도메인 (예: 게시판) → STAGE 2 ~ 4 본인 6 주차 도메인 + 연관 추가 |

**모두 STAGE 1 (영속성 컨텍스트 4 마법 + 변경 감지 / 쓰기 지연 직접 관찰) 은 공통.** 본인 도메인 무관.

## 후보 도메인 + 적합도 (12 개 — 7 명이 1 개씩 + 여유 5)

| # | 도메인 | 1:N / M:N 자연 | N+1 재현 자연 | 면접 가치 | 메모 |
|---|---|---|---|---|---|
| 1 | **게시판** (`board`) | ★★★ | ★★★ | ★★★ | Post + Comment + Author. **N+1 정석**. 가장 흔한 면접 예시 |
| 2 | **이커머스 주문** (`order`) | ★★★ | ★★★ | ★★★ | Order + OrderItem + Product. 6 주차 주문 연장. 다대다 (Product-Category) 추가 가능 |
| 3 | **강의** (`course`) | ★★★ | ★★★ | ★★★ | Course + Enrollment + Student. M:N + 추가 필드 (수강 시점 / 점수). 면접 단골 |
| 4 | **도서관** (`library`) | ★★★ | ★★ | ★★ | Book + Rental + Member. 1:N + 시간순 정렬 |
| 5 | **태그 시스템** (`tag`) | ★★★ | ★★★ | ★★ | Post + Tag (M:N). 조인 테이블 학습. fetch join + 페이징 한계 직격 |
| 6 | **채팅** (`chat`) | ★★★ | ★★★ | ★★ | ChatRoom + Message + User. 페이징 + Lazy 결합. 6 주차 알림 연장 |
| 7 | **블로그** (`blog`) | ★★★ | ★★★ | ★★ | Blog + Post + Comment + Category. 중첩 1:N (Blog 안에 Post 안에 Comment) |
| 8 | **영화** (`movie`) | ★★ | ★★★ | ★★ | Movie + Review + Genre (M:N). 통계 / 평균 평점 결합 |
| 9 | **팀 / 멤버** (`team`) | ★★ | ★★ | ★★ | Team + Member + Role. 양방향 매핑 학습 |
| 10 | **파일 + 댓글** (`file_comment`) | ★★ | ★★ | ★★ | File + Comment. 6 주차 파일업로드 연장 |
| 11 | **이벤트 캘린더** (`calendar`) | ★★ | ★★ | ★★ | Event + Attendee + Schedule. 시간 범위 쿼리 결합 |
| 12 | **카테고리 트리** (`tree`) | ★★★ | ★★ | ★ | 자기참조 1:N. 재귀 쿼리 / 계층 구조. 난도 높음 |

> **N+1 재현 ★★★ 조건** = "메인 Entity 컬렉션 조회 + 각 메인의 자식 컬렉션 접근" 이 자연스러운 사용자 흐름이 있음. 게시판 (게시글 목록 + 댓글 수) 이 가장 정석.

## 도메인별 예상 시나리오 한 줄

| # | 한 줄 시나리오 |
|---|---|
| 1 | `postRepo.findAll()` → 각 `post.getComments().size()` → N+1 재현. JOIN FETCH + @EntityGraph + @BatchSize 비교 |
| 2 | `orderRepo.findAll()` → 각 `order.getOrderItems()` → N+1. 6 주차 OrderPlacedEvent 자동 발행 (@DomainEvents) 결합 |
| 3 | `courseRepo.findAll()` → `course.getEnrollments().size()` → N+1. M:N + 중간 Enrollment Entity |
| 4 | `bookRepo.findAll()` → `book.getRentals()` 순회 → N+1. 대여 이력 / 현재 대여자 시나리오 |
| 5 | `postRepo.findAll()` → `post.getTags()` (M:N) → N+1. fetch join + 페이징 한계 |
| 6 | `chatRoomRepo.findAll()` → `room.getMessages()` 최근 10 개 → N+1 + 페이징 결합 |
| 7 | `blogRepo.findAll()` → `blog.getPosts().getComments()` → 중첩 N+1 (N+M+L) |
| 8 | `movieRepo.findAll()` → `movie.getReviews().average()` → N+1 + 집계 함수 |
| 9 | `teamRepo.findAll()` → `team.getMembers()` → N+1 + 양방향 순환 참조 (JSON 직렬화 주의) |
| 10 | `fileRepo.findAll()` → `file.getComments()` → 6 주차 파일업로드 연장 |
| 11 | `eventRepo.findAll()` → `event.getAttendees()` → N+1 + 시간 범위 |
| 12 | `categoryRepo.findRoots()` → 재귀 `getChildren()` → 깊이만큼 N+1 |

## 학습자 프로필별 추천

| 본인 상황 | 추천 도메인 |
|---|---|
| JPA 처음 / 입문자 | **1 게시판** 또는 **5 태그 시스템** — N+1 가장 명확. 자료 풍부 |
| 면접 가치 최대화 | **1 게시판** / **2 주문** / **3 강의** — 면접 단골 예시 |
| 6 주차 도메인 연장 | **2 주문** (6 주차 주문/결제 연장) / **6 채팅** (6 주차 알림 연장) / **10 파일** (6 주차 파일업로드 연장) |
| M:N 학습 본격 | **3 강의** / **5 태그** / **8 영화** — 중간 Entity / 조인 테이블 |
| fetch join 한계 직격 | **5 태그** / **7 블로그** — 컬렉션 2 개 동시 fetch 시도 → `MultipleBagFetchException` |
| 페이징 + Lazy 결합 | **6 채팅** / **11 캘린더** — 시간순 메시지 페이징 + Lazy |
| 8 주차 (인덱스) 자연 브릿지 | **1 게시판** / **2 주문** — JPA 가 생성한 SQL 의 인덱스 사용 여부 학습 자연 |
| 난도 높지만 고가치 | **12 카테고리 트리** — 자기참조 + 재귀 N+1. 면접 답 기억 강하게 남음 |


━━━━━━━━━━━━━━━━━━━━━━━━━━
STEP 2. 클래스 구조 매핑
━━━━━━━━━━━━━━━━━━━━━━━━━━

도메인별 추천 Entity + Repository + Service 3 종 세트.

| 도메인 | Entity (관계) | Repository | Service / 시연 |
|---|---|---|---|
| 1 게시판 | Post (1) — Comment (N), Post (N) — Author (1) | PostRepo / CommentRepo | PostService.list() 안에서 N+1 재현 |
| 2 주문 | Order (1) — OrderItem (N), OrderItem (N) — Product (1) | OrderRepo / OrderItemRepo | OrderService.summary() N+1 |
| 3 강의 | Course (M) — (Enrollment) — Student (N) | CourseRepo / EnrollmentRepo | CourseService.list() N+1 |
| 4 도서관 | Book (1) — Rental (N) — Member (1) | BookRepo / RentalRepo | BookService.history() N+1 |
| 5 태그 | Post (M) — Tag (N) (PostTag) | PostRepo / TagRepo | PostService.byTag() fetch join + 페이징 |
| 6 채팅 | ChatRoom (1) — Message (N) — User (1) | ChatRoomRepo / MessageRepo | ChatService.recent() N+1 + 페이징 |
| 7 블로그 | Blog (1) — Post (N) — Comment (N) | BlogRepo / PostRepo | BlogService.show() 중첩 N+1 |
| 8 영화 | Movie (1) — Review (N), Movie (M) — Genre (N) | MovieRepo / ReviewRepo | MovieService.list() N+1 |
| 9 팀 | Team (1) — Member (N) — Role (1) | TeamRepo / MemberRepo | TeamService.show() N+1 |
| 10 파일 | File (1) — Comment (N) — User (1) | FileRepo / CommentRepo | FileService.show() N+1 |
| 11 캘린더 | Event (1) — Attendee (N) — User (1) | EventRepo / AttendeeRepo | EventService.day() N+1 |
| 12 트리 | Category (1) — Category (N) (자기참조) | CategoryRepo | CategoryService.tree() 재귀 N+1 |

## 공통 — STAGE 1 손 작성 (모두 동일)

영속성 컨텍스트 4 마법을 손으로 확인. 게시판 도메인 기준:

```java
@Entity
public class Post {
    @Id @GeneratedValue private Long id;
    private String title;
    // ... getter / setter
}

@Service
public class PostDemoService {
    private final PostRepository repo;
    public PostDemoService(PostRepository repo) { this.repo = repo; }

    @Transactional
    public void fourMagic() {
        // (1) 쓰기 지연 — persist() 직후엔 INSERT 안 나감
        Post p = new Post();
        p.setTitle("hello");
        repo.save(p);
        System.out.println("[1] save() 호출 끝 — 아직 INSERT 안 나감");
        // → 콘솔에 SQL 로그 없음 (hibernate.show_sql=true 라도)

        // (2) 1 차 캐시 — 첫 findById = SELECT / 두 번째 = 캐시
        Post a = repo.findById(p.getId()).orElseThrow();
        Post b = repo.findById(p.getId()).orElseThrow();
        System.out.println("[2] a == b ? " + (a == b));     // true ← 동일성

        // (3) 변경 감지 — setter 만 했는데 UPDATE 자동
        a.setTitle("바뀐 제목");
        System.out.println("[3] setTitle() 끝 — 아직 UPDATE 안 나감");
        // → flush 시점 (commit 직전) 에 UPDATE post SET title=? WHERE id=?
    }
    // commit 시점에 INSERT + UPDATE 한꺼번에 발행
}
```

> 핵심: 콘솔의 SQL 로그 (`hibernate.show_sql=true`) 와 println 순서를 직접 보면 "내 코드에서 SQL 안 부르는데 어디서 SQL 나가는가" 가 명확해진다.

## measurements.md 형식 (1, 2, 3, 4, 5, 6 주차와 일관)

자동 누적 형식 그대로:
```
- [07-XX 14:00] s1 · save() 호출 직후 SQL 로그 없음 (쓰기 지연 확인)
- [07-XX 14:15] s1 · findById 두 번 → SELECT 1 회 (1 차 캐시 확인)
- [07-XX 14:30] s1 · findById(1L) == findById(1L) → true (동일성)
- [07-XX 14:45] s1 · setTitle() 후 flush — UPDATE 자동 발행 (변경 감지)
- [07-XX 22:00] s2 · N+1 재현 — Post 10 개 + Comment 접근 → SELECT 11 회 카운트
- [07-XX 22:15] s2 · JOIN FETCH 적용 후 SELECT 1 회 확인
- [07-XX 22:30] s2 · @EntityGraph 적용 — JPQL 안 짜고도 동일 결과
- [07-XX 22:45] s2 · @BatchSize(10) 적용 — IN 절 묶음 SELECT 2 회
- [07-XX 23:00] s2 · fetch join + 페이징 → WARN 메시지 (HHH000104) 확인
- [07-XX 23:15] s2 · 컬렉션 2 개 fetch join → MultipleBagFetchException 재현
- [07-XX 22:00] s3 · LazyInitializationException 재현 — 트랜잭션 밖 getComments()
- [07-XX 22:30] s3 · 6 주차 @Async + AFTER_COMMIT 리스너 안 Lazy 폭발 확인
- [07-XX 22:45] s3 · OSIV ON / OFF 비교 — 커넥션 점유 시간 차이
- [07-XX 23:00] s4 · readOnly=true vs false — 메모리 / 변경 감지 차이
```


━━━━━━━━━━━━━━━━━━━━━━━━━━
환경
━━━━━━━━━━━━━━━━━━━━━━━━━━
- Java 21
- **Spring Boot 3.x** (Spring 6.x 포함, **Jakarta Persistence**, `jakarta.persistence.*` import — `javax.persistence.*` 아님)
- `spring-boot-starter-data-jpa` — Hibernate 6.x 자동 포함
- H2 인메모리 (학습) — `runtimeOnly 'com.h2database:h2'`
- Lombok 권장 (Entity 의 getter/setter 보일러플레이트)

## build.gradle 추가

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'com.h2database:h2'

    // (선택) 6 주차 이벤트 연동 — @DomainEvents 학습 시
    implementation 'org.springframework.boot:spring-boot-starter'

    // (선택) Lombok — Entity 보일러플레이트
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}

compileJava {
    options.compilerArgs += ['-parameters']     // 5, 6 주차에서 익힘
}
```

## application.properties — SQL 로그 / OSIV / fetch size

```properties
spring.datasource.url=jdbc:h2:mem:jpa;DB_CLOSE_DELAY=-1
spring.jpa.hibernate.ddl-auto=create
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.use_sql_comments=true

# Spring Boot 기본 ON — STAGE 3 에서 OFF 비교
spring.jpa.open-in-view=true

# @BatchSize 전역 기본값 — STAGE 2-4 에서 켜기
# spring.jpa.properties.hibernate.default_batch_fetch_size=100
```

> STAGE 1 (영속성 컨텍스트 4 마법) 은 SQL 로그 켜고 시작. 매 단계 콘솔에서 SQL 발행 시점 직접 보기.


━━━━━━━━━━━━━━━━━━━━━━━━━━
STAGE 진행 가이드
━━━━━━━━━━━━━━━━━━━━━━━━━━

## 예상 학습 시간 (한 주 기준)

| 단계 | 시간 | 비고 |
|---|---|---|
| STAGE 1 (영속성 컨텍스트 4 마법 + SQL 로그 관찰) | 2 ~ 3 시간 | **화요일까지 (필수)** |
| **STAGE 2 (N+1 재현 + 해결 4 가지 + fetch join 한계)** ★ | **3 ~ 4 시간** | **목요일까지 (필수)**. 7 주차 가장 중요한 학습 |
| STAGE 3 (Lazy 함정 + OSIV + 6 주차 @Async 회수) | 2 ~ 3 시간 | 함정 직접 재현 |
| STAGE 4 (@Transactional 결합 + readOnly + REQUIRES_NEW) | 1 ~ 2 시간 | 5 주차 ThreadLocal 회수 |
| **합계 (필수)** | **8 ~ 12 시간** | |
| STAGE 5 [여유] (`@DomainEvents` — 6 주차 결합) | 30 ~ 60 분 | 8 주차 (인덱스) 브릿지 |

**배분**:
- 6 주차 (7 ~ 11 시간) 와 비슷한 분량. JPA 가 처음이면 STAGE 1 에 시간 더 들 수 있음
- 직장인 (평일 저녁 1.5 시간 × 5 + 주말 4 시간) — 필수 충분
- 학생 (주말 풀타임 1 일) — 필수 + STAGE 5 까지
- 부담스러우면 **STAGE 2 (N+1) + STAGE 3 (Lazy 함정) 가 면접 최강** — 시간 부족 시 STAGE 4 는 짧게

### [화 11:00 — Draft PR 마감 + 겪기 발표] — STAGE 1

> 7 주차는 **STAGE 1 (영속성 컨텍스트 4 마법 SQL 로그로 관찰) 까지 화요일 분량**. STAGE 2 (N+1) 부터는 목요일까지.

#### ▸ STAGE 1 — 영속성 컨텍스트 4 마법 (필수)

**목표**: SQL 안 부르는데 어떻게 INSERT/UPDATE 가 자동으로 나가는가 — 콘솔 SQL 로그로 직접 확인.

##### 1-1. 쓰기 지연 — persist() 직후 INSERT 안 나감

```java
@Transactional
public void writeBehind() {
    Post p = new Post("hello");
    repo.save(p);
    System.out.println("[A] save() 호출 끝");
    // ← 콘솔에 INSERT 로그 아직 없음

    Post p2 = new Post("world");
    repo.save(p2);
    System.out.println("[B] save() 두 번째 끝");
    // ← 아직 INSERT 없음

    System.out.println("[C] 메서드 종료 → commit 시점에 INSERT 2 회 발행");
}
// 콘솔: [A] [B] [C] 출력 후 INSERT into post ... INSERT into post ...
```

**관찰 포인트**:
- `save()` 직후엔 SQL 안 나감. 트랜잭션 commit 직전 flush 시점에 일괄 발행
- `em.flush()` 직접 호출하면 즉시 발행 — 강제 flush
- 1 차 캐시 + 쓰기 지연 덕에 JPA 가 **JDBC batch insert / update 순서 최적화** 가능
- JdbcTemplate 시절엔 `jdbc.update("INSERT ...")` 호출 시점 = SQL 실행 시점. 6 주차까지의 직관과 다름

##### 1-2. 1 차 캐시 + 동일성 보장

```java
@Transactional
public void firstCache() {
    Post a = repo.findById(1L).orElseThrow();
    System.out.println("[A] 첫 findById — 콘솔에 SELECT 로그 1 회");

    Post b = repo.findById(1L).orElseThrow();
    System.out.println("[B] 두 번째 findById — 콘솔에 SELECT 로그 없음 (캐시)");

    System.out.println("a == b ? " + (a == b));         // true
    System.out.println("a.equals(b) ? " + a.equals(b)); // true
}
```

**관찰 포인트**:
- 두 번째 `findById` 는 1 차 캐시에서 반환 → SELECT 안 나감
- 같은 인스턴스 (`==` true) — JdbcTemplate 시절엔 매 조회마다 새 객체
- 다른 트랜잭션 / EntityManager 에서는 다른 인스턴스 (`==` false)

##### 1-3. 변경 감지 (Dirty Checking) — 가장 마법 같은 자리

```java
@Transactional
public void dirtyChecking() {
    Post p = repo.findById(1L).orElseThrow();
    System.out.println("[A] 조회 끝");

    p.setTitle("바뀐 제목");
    System.out.println("[B] setTitle() 끝 — UPDATE SQL 안 부름");
    // ← 콘솔에 UPDATE 로그 아직 없음

    // 내가 repo.save(p) / em.merge(p) 한 줄도 안 부름!
    System.out.println("[C] 메서드 종료 → flush 시점에 UPDATE 자동");
}
// 콘솔: SELECT 1 회 / UPDATE 1 회 (commit 직전)
```

**관찰 포인트**:
- `setTitle` 만 했는데 UPDATE 가 자동으로 나감 — **가장 큰 충격 포인트**
- 메커니즘: 조회 시점에 영속성 컨텍스트가 Entity 스냅샷 저장 → flush 시점에 현재 값과 스냅샷 비교 → 변경된 컬럼만 UPDATE
- 그러므로 **메모리 비용** 이 있음 — 영속성 컨텍스트는 모든 영속 Entity 의 스냅샷을 들고 있음
- `@Transactional(readOnly = true)` 면 스냅샷 안 만듦 → 변경 감지 X / 메모리 절약

##### 1-4. 영속성 컨텍스트 수명 = 트랜잭션 수명

```java
@Transactional
public Post returnEntity(Long id) {
    Post p = repo.findById(id).orElseThrow();
    return p;
}
// 호출자
Post p = svc.returnEntity(1L);
// 여기는 트랜잭션 밖 — 영속성 컨텍스트 close 됨
p.setTitle("바뀐 제목");
// ← 변경 감지 안 됨 (영속성 컨텍스트 밖)
// p.getComments().size();   // LazyInitializationException 위험
```

**관찰 포인트**:
- 트랜잭션 종료 = 영속성 컨텍스트 close = Entity 가 "준영속" (detached) 상태
- 준영속 상태에서 setter 호출은 그냥 자바 객체 setter — DB 반영 X
- Lazy 컬렉션 / 객체 접근 → `LazyInitializationException`
- 트랜잭션 안에서 끝낼지 / DTO 로 변환 후 반환할지 — 설계 결정 (STAGE 3 에서 다룸)


### [목 11:00 — Ready PR 전환 + 코드 발표] — STAGE 2 ~ STAGE 4

> STAGE 1 (4 마법 관찰) 은 화요일까지. 목요일까지는 N+1 재현 / 해결 4 가지 (STAGE 2) → Lazy 함정 (STAGE 3) → @Transactional 결합 (STAGE 4).

#### ▸ STAGE 2 — N+1 재현 + 해결 4 가지 (필수, **7 주차 가장 중요**)

##### 2-1. 순진한 코드 — N+1 재현 (★ 핵심 학습 단계)

**🔴 핵심 학습 단계** — 한 번에 정답 짜지 말고 순진한 버전 → 카운트 측정 → 해결 4 가지 순서로.

```java
@Entity
public class Post {
    @Id @GeneratedValue private Long id;
    private String title;

    @OneToMany(mappedBy = "post")               // 기본 fetch=LAZY
    private List<Comment> comments = new ArrayList<>();
    // ... getter
}

@Entity
public class Comment {
    @Id @GeneratedValue private Long id;
    private String content;

    @ManyToOne                                   // 기본 fetch=EAGER (주의)
    @JoinColumn(name = "post_id")
    private Post post;
}

// 시나리오
@Transactional
public void listPosts() {
    List<Post> posts = postRepo.findAll();                  // SELECT * FROM post — 1 회
    for (Post p : posts) {
        System.out.println(p.getTitle() + " 댓글 " + p.getComments().size());
        // ← 매 Post 마다 SELECT * FROM comment WHERE post_id=? — N 회
    }
    System.out.println("총 SQL = 1 + " + posts.size() + " 회");
}
```

**SQL 로그 (Post 10 개일 때)**:
```
SELECT * FROM post                                  -- 1
SELECT * FROM comment WHERE post_id = 1             -- 2
SELECT * FROM comment WHERE post_id = 2             -- 3
... (총 11 회)
```

**측정 매트릭스**:

| Post 수 | 예상 SQL | 실제 측정 |
|---|---|---|
| 10 | 11 (1+10) | ____ |
| 50 | 51 | ____ |
| 100 | 101 | ____ |

→ 실무에서 Post 가 100 개면 101 회 SQL. DB 응답 시간 50ms 면 총 5 초.

##### 2-2. 해결 (a) JPQL JOIN FETCH

```java
public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("select p from Post p join fetch p.comments")
    List<Post> findAllWithComments();
}

// 시나리오
List<Post> posts = postRepo.findAllWithComments();      // SELECT 1 회 (JOIN)
for (Post p : posts) {
    p.getComments().size();                              // 캐시 — SELECT X
}
```

**SQL 로그**:
```
SELECT p.*, c.* FROM post p LEFT JOIN comment c ON c.post_id = p.id      -- 1 회
```

**관찰 포인트**:
- 한 쿼리로 끝 — N+1 해소
- 결과 행 수 — Post N 개 × Comment M 개 = N×M 행 (중복). Hibernate 가 자바 객체로 묶을 때 중복 제거
- `select distinct p from ...` 로 SQL 레벨 distinct (Hibernate 6.x 부터 기본 동작)
- **fetch join 의 첫 번째 한계** — 결과 행 수가 폭증 (Cartesian product) → 다대다 fetch join 위험

##### 2-3. 해결 (b) `@EntityGraph` — Spring Data 선언형

```java
public interface PostRepository extends JpaRepository<Post, Long> {
    @EntityGraph(attributePaths = {"comments"})
    List<Post> findAll();                                  // 기본 메서드 오버라이드
}
```

**관찰 포인트**:
- JPQL 안 짜고도 동일 효과 (LEFT JOIN FETCH)
- 메서드 시그니처는 그대로 → 호출자 코드 변경 0
- 복잡한 JOIN 조건이 있을 땐 JPQL 이 더 유연. 단순한 경우 `@EntityGraph` 권장
- `@EntityGraph(attributePaths = {"comments", "author"})` 처럼 여러 연관 동시 — 단 컬렉션 2 개 동시는 `MultipleBagFetchException` (2-5 참고)

##### 2-4. 해결 (c) `@BatchSize` / 전역 `default_batch_fetch_size`

```java
@Entity
public class Post {
    @OneToMany(mappedBy = "post")
    @org.hibernate.annotations.BatchSize(size = 100)       // 한 번에 100 개씩 IN
    private List<Comment> comments;
}

// 또는 application.properties 전역
// spring.jpa.properties.hibernate.default_batch_fetch_size=100
```

**SQL 로그 (Post 10 개, BatchSize 5)**:
```
SELECT * FROM post                                              -- 1
SELECT * FROM comment WHERE post_id IN (?, ?, ?, ?, ?)          -- 2 (5 개)
SELECT * FROM comment WHERE post_id IN (?, ?, ?, ?, ?)          -- 3 (나머지 5 개)
```

**관찰 포인트**:
- 1 + N 회 → 1 + ⌈N / BatchSize⌉ 회
- 결과 행 중복 없음 (fetch join 과 달리 Cartesian product X)
- **페이징과 같이 써도 안전** — fetch join 의 페이징 한계 회피
- Aggregate Root 가 자식 컬렉션 여러 개 있어도 OK — `MultipleBagFetchException` 회피
- 트레이드오프 — Lazy 그대로 두고 batch 만 묶기 때문에 어쨌든 추가 SELECT (1+k) 발생. fetch join 이 1 회로 줄이는 것보다는 많음
- **실무에서 가장 자주 권장되는 패턴** — fetch join 의 한계가 너무 많아서

##### 2-5. fetch join 의 한계 — 페이징 + `MultipleBagFetchException`

**한계 1 — 1:N + 페이징 동시 = WARN + 메모리 페이징**

```java
@Query("select p from Post p join fetch p.comments")
List<Post> findAllWithComments(Pageable pageable);          // Pageable 추가
```

**WARN 로그** (Hibernate):
```
HHH000104: firstResult/maxResults specified with collection fetch;
applying in memory
```

**문제**:
- DB 에서 모든 Post + Comment 가져온 후 메모리에서 페이징 처리
- Post 가 100 만 개면 모두 메모리에 올림 → OOM
- 1:N 컬렉션이 아닌 다대일 (`fetch p.author`) 면 OK

**해결**:
- 페이징은 `@BatchSize` 와 조합 — Post 만 페이징으로 가져온 후 Comment 는 batch 로
- 또는 Post 만 페이징으로 ID 만 가져온 후 두 번째 쿼리로 Post + Comment fetch (2-phase)

**한계 2 — 컬렉션 2 개 동시 fetch = `MultipleBagFetchException`**

```java
@Query("select p from Post p join fetch p.comments join fetch p.tags")
List<Post> findAllRich();                                    // 폭발
```

**예외**:
```
org.hibernate.loader.MultipleBagFetchException:
cannot simultaneously fetch multiple bags: [Post.comments, Post.tags]
```

**왜?** — fetch join 2 개 = N × M × L 행 폭증. Hibernate 가 자바 객체 매핑 불가능 판단

**해결 3 가지**:
- (a) `List` → `Set` 으로 — Set 은 `bag` 이 아니라 OK
- (b) 컬렉션 1 개만 fetch join + 나머지는 `@BatchSize` — **가장 권장**
- (c) 컬렉션 2 개 다 `@BatchSize` 만

##### 2-6. STAGE 2 측정 매트릭스

| 해결책 | SQL 회수 (Post 10) | 행 중복 | 페이징 OK | 컬렉션 2 개 |
|---|---|---|---|---|
| 순진한 코드 (N+1) | 11 | X | OK | OK |
| JOIN FETCH | 1 | O (Cartesian) | X (메모리) | X (`MultipleBagFetchException`) |
| @EntityGraph | 1 | O | X | X |
| @BatchSize (size=5) | 3 (1 + 2) | X | OK ✓ | OK ✓ |
| @BatchSize (size=100) | 2 (1 + 1) | X | OK ✓ | OK ✓ |
| 전역 default_batch_fetch_size | 위와 동일 | X | OK ✓ | OK ✓ |

→ 실무 권장: **컬렉션 1 개 = fetch join / 나머지 = @BatchSize**. 또는 전역 `default_batch_fetch_size=100` 으로 깔고 가기


#### ▸ STAGE 3 — Lazy 로딩 + 컨텍스트 함정 (필수)

##### 3-1. fetch=LAZY vs EAGER 기본값 매트릭스

| 어노테이션 | 기본 fetch |
|---|---|
| `@OneToMany` | **LAZY** |
| `@ManyToMany` | **LAZY** |
| `@ManyToOne` | **EAGER** ← 주의 |
| `@OneToOne` | **EAGER** ← 주의 |

**관찰 포인트**:
- `@ManyToOne` 기본 EAGER 는 의도와 다른 SELECT 폭발 위험 — `@ManyToOne(fetch = FetchType.LAZY)` 명시 권장 (실무 컨벤션)
- `@OneToMany` LAZY 는 N+1 의 원천. JOIN FETCH / @BatchSize 로 case-by-case 해결
- 모든 연관을 EAGER 로 하면 Cartesian product / 메모리 폭증

##### 3-2. `LazyInitializationException` 재현

```java
@Transactional
public Post fetchPost(Long id) {
    return repo.findById(id).orElseThrow();
}

// 컨트롤러 / 호출자 (트랜잭션 밖)
Post p = svc.fetchPost(1L);
p.getComments().size();             // ← LazyInitializationException
```

**왜?**:
- `fetchPost` 트랜잭션 종료 = 영속성 컨텍스트 close
- 반환된 Post 는 준영속 (detached)
- Lazy 컬렉션은 영속성 컨텍스트 없으면 fetch 불가 → 예외

**해결 5 가지**:
| 방법 | 코드 / 트레이드오프 |
|---|---|
| (a) DTO 변환 후 반환 | 트랜잭션 안에서 `new PostDto(p, p.getComments().size())` 변환. **가장 권장** |
| (b) JOIN FETCH | 컬렉션을 미리 가져옴 |
| (c) `@EntityGraph` | 동일 효과, 선언형 |
| (d) OSIV ON | 컨트롤러 / View 까지 컨텍스트 유지. Spring Boot 기본. **함정 있음** (3-4) |
| (e) `Hibernate.initialize(p.getComments())` | 트랜잭션 안에서 강제 초기화 |

##### 3-3. 6 주차 `@Async + AFTER_COMMIT` 회수 — Lazy 폭발 함정

```java
@Service
public class OrderService {
    @Transactional
    public void placeOrder(...) {
        Order order = new Order(...);
        orderRepo.save(order);
        publisher.publishEvent(new OrderPlacedEvent(order.getId()));
    }
}

@Component
public class NotifyListener {
    @Async
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void on(OrderPlacedEvent e) {
        Order o = orderRepo.findById(e.orderId()).orElseThrow();   // ① 새 트랜잭션 필요
        o.getOrderItems().size();                                    // ② Lazy 폭발 위험
    }
}
```

**문제**:
- `@Async + AFTER_COMMIT` = **새 스레드 + 본 트랜잭션 끝**
- 영속성 컨텍스트 / `TransactionSynchronizationManager` 다 날아감 — 5 주차 ThreadLocal 회수
- 리스너 안에서 `findById` 호출 시 새 트랜잭션 필요 — `@Transactional(REQUIRES_NEW)` 명시
- Lazy 컬렉션 접근하려면 새 트랜잭션 안에서 fetch join 또는 `@EntityGraph`

**해결 패턴**:
```java
@Component
public class NotifyListener {
    @Async
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)       // ★
    public void on(OrderPlacedEvent e) {
        // 이제 영속성 컨텍스트 있음 — Lazy 안전
        Order o = orderRepo.findByIdWithItems(e.orderId());      // fetch join
        // ...
    }
}
```

**더 권장 — 이벤트 payload 에 필요한 데이터 미리 포함**:
```java
public record OrderPlacedEvent(Long orderId, List<OrderItemSummary> items) {}
// publisher 가 트랜잭션 안에서 미리 변환해서 전달 → 리스너는 DB 안 봐도 됨
```

##### 3-4. OSIV (Open Session In View) — Spring Boot 기본의 함정

```properties
# Spring Boot 기본
spring.jpa.open-in-view=true
```

**효과**:
- 영속성 컨텍스트가 **컨트롤러 / View 렌더링까지** 살아있음
- 컨트롤러에서 `entity.getComments()` Lazy 접근 OK
- 학습 / 개발 단계엔 편리

**함정**:
- HTTP 응답이 끝날 때까지 **DB 커넥션을 잡고 있음**
- 외부 API 호출 (수 초) 후 응답하면 → 그 시간 동안 커넥션 점유
- 트래픽 많은 운영 환경 → **커넥션 풀 고갈** → 다른 요청 대기 / timeout

**실무 권장**:
```properties
spring.jpa.open-in-view=false
```

→ 서비스 계층에서 모든 Lazy 초기화 / DTO 변환 강제. 컨트롤러는 DTO 만 받음

**관찰 포인트**:
- 학습 단계 (이 STAGE) — ON 으로 두고 함정 인지
- 본인 도메인 STAGE 4 부터는 OFF 권장

##### 3-5. 5 주차 / 6 주차 도구의 자리 정리

| 도구 | 자리 | 7 주차 결합 |
|---|---|---|
| 5 주차 `@Transactional` (AOP) | 트랜잭션 시작 / commit / rollback | EntityManager 생성 / close 자리 |
| 5 주차 `TransactionSynchronizationManager` (ThreadLocal) | 현재 트랜잭션 / 연결 보관 | EntityManager 도 같이 보관 |
| 6 주차 `@TransactionalEventListener` | commit 후 콜백 | 영속성 컨텍스트는 이미 close — Lazy 폭발 위험 |
| 6 주차 `@Async` | 별 스레드 | 영속성 컨텍스트 없음. 새 트랜잭션 필요 |
| 7 주차 영속성 컨텍스트 | Entity 1 차 캐시 + 변경 감지 | 모든 위 메커니즘이 결합 |


#### ▸ STAGE 4 — @Transactional + 영속성 컨텍스트 결합 (필수)

##### 4-1. 트랜잭션 = 영속성 컨텍스트 수명 직접 관찰

```java
@Transactional
public void test() {
    EntityManager em = ...;     // 주입
    Post a = repo.findById(1L).orElseThrow();
    System.out.println("a 영속? " + em.contains(a));        // true

    // 다른 메서드 호출 — 같은 트랜잭션
    nestedMethod(a.getId());
}

@Transactional   // REQUIRED 기본 — 같은 트랜잭션 참여
public void nestedMethod(Long id) {
    Post b = repo.findById(id).orElseThrow();
    // 같은 영속성 컨텍스트 → 1 차 캐시에서 반환
}
```

##### 4-2. `REQUIRES_NEW` — 새 영속성 컨텍스트

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void newContext(Long id) {
    Post c = repo.findById(id).orElseThrow();
    // 새 트랜잭션 + 새 영속성 컨텍스트
    // 호출자의 a 와 c 는 다른 인스턴스 (== false)
}
```

**관찰 포인트**:
- 6 주차 `@Async + AFTER_COMMIT` 의 새 스레드와 같은 결 — 새 컨텍스트
- 호출자의 변경이 commit 되기 전 새 트랜잭션이 같은 row 를 본다면? → 격리 수준에 따라 (2 주차 회수)

##### 4-3. `@Transactional(readOnly = true)` — 메모리 / 변경 감지 X

```java
@Transactional(readOnly = true)
public List<PostDto> list() {
    return repo.findAll().stream()
        .map(PostDto::from)
        .toList();
    // Entity 의 스냅샷 X → 변경 감지 X → 메모리 절약
    // flush 모드 MANUAL → 자동 flush X
}
```

**관찰 포인트**:
- 조회 전용 메서드에 명시 권장
- 일부 DB (MySQL) — 트랜잭션 자체를 readonly 모드로 시작 → DB 레벨 최적화
- 실수로 setter 호출해도 UPDATE 안 나감 — 안전망


━━━━━━━━━━━━━━━━━━━━━━━━━━
─── 여기 아래는 선택 (시간 여유 시) ───
━━━━━━━━━━━━━━━━━━━━━━━━━━

### [선택] ▸ STAGE 5 — `@DomainEvents` 6 주차 결합 + 8 주차 브릿지

> ⏰ **언제 하나**: Ready PR (목 11:00) 이후 **여유 시에만**. STAGE 1 ~ 4 가 우선. 늦어도 **8 주차 시작 전** 까지 안 해도 됨.

##### 5-1. `AbstractAggregateRoot` — publisher 없이 이벤트 발행

```java
@Entity
public class Order extends AbstractAggregateRoot<Order> {
    @Id @GeneratedValue private Long id;
    private BigDecimal amount;

    public void place() {
        // ... 도메인 로직
        registerEvent(new OrderPlacedEvent(this.id, this.amount));    // publisher 없이
    }
}

// 호출자
order.place();
orderRepo.save(order);          // ← save 시점에 등록된 이벤트 자동 publish
```

**관찰 포인트**:
- `AbstractAggregateRoot.@DomainEvents` 메서드가 `repository.save()` 호출 시 자동 실행
- publisher 주입 불필요 → 도메인 객체가 Spring 의존성 없이 순수 (DDD 친화)
- 단 `save` 거쳐야만 발행 — 호출 흐름 명시 필요

##### 5-2. 8 주차 (인덱스 / 쿼리 튜닝) 예고

7 주차에서 본인이 짠 JPQL / Hibernate 가 생성한 SQL 을 8 주차에서:
- 인덱스 사용 여부 (`EXPLAIN`)
- 슬로우 쿼리 발견 + 인덱스 추가
- 쿼리 플랜 읽는 법

→ JPA 의 마법을 알았으니 이제 그 마법이 만든 SQL 을 점검하는 단계.


━━━━━━━━━━━━━━━━━━━━━━━━━━
금지 키워드 — STAGE 1 ~ 2 동안만 (검색 금지)
━━━━━━━━━━━━━━━━━━━━━━━━━━
- `JPABuddy` / `IntelliJ JPA Console` — STAGE 2 N+1 재현 후 도구 사용 OK. 그 전엔 콘솔 SQL 로그로 직접 보기
- QueryDSL — JPQL 의 type-safe 빌더. 학습 후 익히면 좋지만 본 학습 후
- Spring Data JPA `@Query` 의 native SQL — JPA 학습 범위. 8 주차 (인덱스) 에서
- 캐시 (1 차 캐시 외) — 2 차 캐시 / EhCache / Redis. 9 주차 (캐시) 영역
- MapStruct / ModelMapper — DTO 변환 도구. 학습 후
- `@Query(nativeQuery = true)` — JPA 학습 후 8 주차
- Hibernate 6 의 `@FetchProfile` — 학습 범위 외


━━━━━━━━━━━━━━━━━━━━━━━━━━
학습 기록 (자유)
━━━━━━━━━━━━━━━━━━━━━━━━━━

### 6 주차 회상 — 7 주차로 이어지는 지점

| 6 주차에서 본 것 | 7 주차에서 확장 |
|---|---|
| `@TransactionalEventListener` 가 `TransactionSynchronization` 콜백 등록 | 같은 메커니즘으로 영속성 컨텍스트도 close 자리 등록 |
| `@Async + AFTER_COMMIT` 새 스레드 = ThreadLocal 날아감 | 영속성 컨텍스트도 같이 날아감 → Lazy 폭발 |
| 5 주차 양파 한계를 시간축으로 풀었듯 | 7 주차 영속성 컨텍스트는 또 다른 암묵적 메커니즘 (변경 감지 + 지연 쓰기) |
| `@DomainEvents` 살짝 예고 (STAGE 5) | 7 주차 STAGE 5 에서 `AbstractAggregateRoot` 본격 |
| Event = 명시적 발행 (publishEvent) | JPA = 명시적 SQL X. 객체 변경만 → 자동 SQL |

### 7 주차 참고 질문 (답하고 싶은 만큼만)
- 영속성 컨텍스트의 4 마법 각각 본인 코드로 1 회씩 확인했나
- 변경 감지가 메모리에 비용을 발생시키는 이유 + 해결 (`readOnly = true`)
- N+1 문제가 fetch=LAZY 의 의도와 어떻게 충돌하는가
- JOIN FETCH 와 `@EntityGraph` — 본인이 어느 쪽 쓰는가 + 이유
- fetch join 의 페이징 한계 + `MultipleBagFetchException` 본인 답
- `@BatchSize` 가 fetch join 의 한계를 어떻게 우회하나
- `LazyInitializationException` 5 가지 해결 중 본인 권장 + 이유
- OSIV 의 트레이드오프 — 본인 프로젝트에서 ON / OFF 결정 기준
- 6 주차 `@Async + AFTER_COMMIT` 함정이 7 주차에서 어떻게 완성되나
- `@DomainEvents` 가 6 주차 `publishEvent` 보다 도메인 객체에 어떤 이점을 주나

### 면접 단골 + 본인 답
- **"영속성 컨텍스트 4 마법"** — 1 차 캐시 / 변경 감지 / 쓰기 지연 / 동일성 보장
- **"`em.persist()` 직후 SELECT 안 나가는 이유"** — 1 차 캐시. 같은 트랜잭션 안 `findById` = 캐시 반환
- **"변경 감지가 동작하는 시점 + 메커니즘"** — flush 시점 (commit 직전). 스냅샷과 현재 값 비교
- **"N+1 발생 원리 + 해결 4 가지"** — fetch=LAZY + 컬렉션 순회. JOIN FETCH / @EntityGraph / @BatchSize / 전역 batch_fetch_size
- **"fetch join 의 페이징 한계"** — 1:N + Pageable = WARN + 메모리 페이징. 컬렉션 1 개만 fetch + 나머지 @BatchSize
- **"`MultipleBagFetchException`"** — 컬렉션 2 개 동시 fetch join = Cartesian product 폭증. `List → Set` 또는 @BatchSize
- **"`LazyInitializationException` 발생 조건 + 해결"** — 영속성 컨텍스트 close 후 Lazy 접근. DTO 변환 / fetch join / @EntityGraph
- **"OSIV 의 트레이드오프"** — 학습 편의 vs 커넥션 풀 점유. 실무는 OFF + 서비스 계층 DTO 변환
- **"`@Transactional(readOnly = true)` 효과"** — 변경 감지 X / 메모리 절약 / 일부 DB 최적화
- **"`@OneToMany` 기본 LAZY vs `@ManyToOne` 기본 EAGER"** — `@ManyToOne` 도 LAZY 명시 권장 (실무)
- **"6 주차 `@Async + AFTER_COMMIT` 에서 Lazy 폭발"** — 영속성 컨텍스트 / `TransactionSynchronizationManager` 날아감. `REQUIRES_NEW` + payload 풍부하게
- **"`@DomainEvents` vs `publishEvent`"** — Aggregate Root 가 Spring 의존성 없이 발행 (DDD 친화). `repository.save` 거쳐야 발행
- **"OSIV OFF 가 실무 권장인 진짜 이유"** — 학습 편의 vs 커넥션 풀 점유. 외부 API 호출 수 초 동안 톰캣 스레드가 DB 커넥션 잡고 있음 → 풀 고갈 → 시스템 장애. 운영은 OFF + 서비스 계층 DTO 변환 강제
- **"JPA + MyBatis 혼용 시 stale 데이터 조회 함정"** — 한 트랜잭션 안 JPA setter 변경 → 아직 flush 안 됨 → 같은 트랜잭션 MyBatis SELECT → 옛 값. 해결 = `em.flush()` 강제 호출

### 실무 확장 화두 (스터디 토론 / 면접 후속 질문)
- **JdbcTemplate vs JPA — 언제 어느 쪽?**: 단순 CRUD / 통계 / 리포팅 = JdbcTemplate. 복잡한 도메인 / 변경 감지 활용 / 1 차 캐시 = JPA. 한 프로젝트에 둘 다 OK
- **`em.flush()` vs commit 자동 flush**: 명시 flush 는 강제 시점 제어. 1 차 캐시 비우려면 `em.clear()` 까지. 보통 안 씀
- **JPA 가 생성하는 SQL 의 한계**: 동적 조건 (검색 폼) 은 JPQL 로 짜기 어려움 → QueryDSL / native SQL. 8 주차 영역
- **`fetch = LAZY` 가 모두 권장인 이유**: EAGER 는 어디서든 N+1 / Cartesian / 메모리 폭증 위험. LAZY + 필요 시점에 fetch join 이 정석
- **`@OneToMany(cascade = ALL)` + `orphanRemoval`**: 부모 저장 시 자식 자동 INSERT / 부모에서 자식 제거 시 자동 DELETE. 강한 결합 — Aggregate Root 패턴
- **`Hibernate` 6 의 변경**: distinct fetch join 기본 / 6.6+ 의 `@FetchProfile` 등. 명세 변화 추적
- **읽기 전용 트랜잭션의 DB 레벨 효과**: MySQL `START TRANSACTION READ ONLY` / PostgreSQL `SET TRANSACTION READ ONLY` — Hibernate 가 알아서 발행
- **OSIV 의 대체 — `EntityGraph` + 서비스 계층 DTO 변환**: OSIV OFF + 모든 조회는 서비스 계층에서 끝 + 컨트롤러는 DTO 만
- **Aggregate Root 패턴 (DDD)**: 외부에서는 Root 만 참조. 자식 Entity 는 Root 거쳐서만 접근 → 영속성 + 비즈니스 일관성
- **`@DomainEvents` + Transactional Outbox**: 7 주차 + 6 주차 결합. Aggregate Root 의 `registerEvent` → save 시 자동 발행 → AFTER_COMMIT 리스너가 outbox 테이블에 INSERT → 별도 worker 가 MQ 발행
- **OSIV OFF 의 진짜 무서움 (대용량 트래픽)**: 학습 단계엔 OSIV ON 이 편리하지만, 실시간 트래픽 많은 환경 (핀테크 / 트레이딩) 에서는 외부 API 호출 (수 초) 동안 톰캣 스레드가 응답 기다리며 **DB 커넥션 점유** → 커넥션 풀 순식간에 고갈 → 전체 시스템 장애. 운영에서 OSIV OFF 는 선택이 아닌 생존
- **JPA + MyBatis / JdbcTemplate 혼용 시 flush 함정**: 한 트랜잭션 안에서 JPA 로 setter 변경 → 아직 flush 안 됨 → 같은 트랜잭션의 MyBatis SELECT → **stale 데이터 조회**. 통계 / 레거시 호환으로 둘 다 쓰는 경우 발생. 해결 = JPA 변경 후 `em.flush()` 강제 호출 → MyBatis 가 최신 값 보게 함

### N+1 해결 선택 매트릭스 (면접 답변 기준)

| 상황 | 선택 | 이유 |
|---|---|---|
| 단순 1:N + 페이징 X | JOIN FETCH 또는 `@EntityGraph` | 한 쿼리로 끝 |
| 1:N + 페이징 O | `@BatchSize` (컬렉션) + 페이징 쿼리 | fetch join + 페이징 = 메모리 페이징 함정 |
| 컬렉션 2 개 동시 fetch | (1 개만 fetch join) + (나머지 `@BatchSize`) | `MultipleBagFetchException` 회피 |
| 전역 적용 | `hibernate.default_batch_fetch_size=100` | 깔고 가면 대부분 N+1 자동 완화 |
| 단순 다대일 (`@ManyToOne`) | fetch=LAZY 명시 + 필요 시 fetch join | EAGER 기본은 의도와 다른 SELECT |
| 컬렉션 무관 단건 조회 | fetch=LAZY + 필요 시점에 fetch | EAGER 는 어디서든 위험 |


━━━━━━━━━━━━━━━━━━━━━━━━━━
막힐 때
━━━━━━━━━━━━━━━━━━━━━━━━━━
1. 직접 시도 (`CLAUDE.md` 룰)
2. AI 에 물어보기 — 3 단계 힌트만 받음 (코드 직답 X)
3. 디스코드 `#질문` 채널 — 코드 + 본인 Entity / 쿼리 + SQL 로그 함께

**N+1 인지 어떻게 확인?**:
1. `spring.jpa.show-sql=true` 켜고 콘솔 SQL 카운트
2. `p6spy` / `datasource-proxy` 같은 SQL 카운트 도구 (학습 후)
3. JPA Buddy / IntelliJ JPA Console — STAGE 2 후 도구 사용 OK

**`LazyInitializationException` 발생 시 체크리스트**:
1. 호출 메서드에 `@Transactional` 있는가
2. 트랜잭션 안에서 Lazy 컬렉션 / 객체 접근했는가
3. 반환된 Entity 를 트랜잭션 밖에서 접근하는가 → DTO 변환 필요
4. `@Async` 또는 `AFTER_COMMIT` 리스너 안인가 → `REQUIRES_NEW` + fetch join

**`MultipleBagFetchException` 발생 시**:
1. 컬렉션 2 개 동시 fetch join 시도했는가
2. `List` → `Set` 으로 변경하면 회피 (단 순서 / 중복 제어 다름)
3. 컬렉션 1 개만 fetch + 나머지 `@BatchSize` 권장

**변경 감지가 동작 안 함**:
1. Entity 가 영속 상태인가 (`em.contains(entity)` 확인)
2. 트랜잭션 안인가 (`@Transactional` 메서드 안)
3. setter 호출 후 자동 flush — `em.flush()` 강제 호출로 확인
4. `@Transactional(readOnly = true)` 인가 → readOnly 면 변경 감지 X

**쓰기 지연으로 INSERT 가 너무 늦게 나감**:
1. JPA 의 기본 동작 — flush 시점에 발행 (commit 직전)
2. 즉시 발행 원하면 `em.flush()` 명시 호출
3. ID 자동 생성 전략 (`@GeneratedValue(strategy = IDENTITY)`) 은 persist 즉시 INSERT (DB 가 ID 만들어줘야 하므로)

**JPA 변경 후 MyBatis / native SQL 가 옛 값을 봄 (stale 조회)**:
1. 한 트랜잭션 안에서 JPA 의 setter 변경 → 아직 flush 안 됨 → MyBatis SELECT 가 옛 값 조회
2. 해결: JPA 변경 후 `em.flush()` 강제 호출 → MyBatis 가 최신 값 봄
3. 또는 트랜잭션 분리 — 단 격리 수준 / 일관성 별개 고민
