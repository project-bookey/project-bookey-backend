package app.bookey.domain.inquiry;

import app.bookey.common.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객문의에 붙는 사진(스크린샷). 업로드 시에는 inquiry_id 가 비어 있고(임시), 문의를 만들 때 붙인다.
 * 24시간 안에 어디에도 붙지 않았거나 문의가 지워져 떨어진 사진은 정리 배치가 지운다(post_images 와 같은 정책).
 */
@Getter
@Entity
@Table(name = "inquiry_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "inquiry_id")
    private Long inquiryId;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

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

    @Builder
    private InquiryImage(Long userId, String storageKey, String url, String contentType, int byteSize,
                         Integer width, Integer height) {
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

    /** 아직 어떤 문의에도 붙지 않은 임시 업로드인지. 붙이기는 조건부 UPDATE({@code attachIfDetached})로 한다. */
    public boolean isDetached() {
        return inquiryId == null;
    }
}
