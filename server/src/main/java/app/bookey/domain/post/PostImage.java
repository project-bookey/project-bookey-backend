package app.bookey.domain.post;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 독후감에 붙는 사진. 업로드 시에는 post_id 가 비어 있고(임시), 독후감에 붙일 때 채운다. */
@Getter
@Entity
@Table(name = "post_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    private Integer width;

    private Integer height;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Builder
    private PostImage(Long userId, String storageKey, String url, String contentType,
                      int byteSize, Integer width, Integer height) {
        this.userId = userId;
        this.storageKey = storageKey;
        this.url = url;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.width = width;
        this.height = height;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** 아직 어떤 독후감에도 붙지 않은 임시 업로드인지. */
    public boolean isDetached() {
        return postId == null;
    }

    public void attach(Long postId, int order) {
        this.postId = postId;
        this.sortOrder = (short) order;
    }

    public void detach() {
        this.postId = null;
        this.sortOrder = 0;
    }
}
