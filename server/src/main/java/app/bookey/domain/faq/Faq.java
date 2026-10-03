package app.bookey.domain.faq;

import app.bookey.common.support.BaseTimeEntity;
import app.bookey.domain.inquiry.InquiryCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 자주 묻는 질문. 분류는 고객문의 유형과 같은 값을 쓰고, 순서는 전역 하나(sortOrder)로 관리한다. */
@Getter
@Entity
@Table(name = "faqs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faq extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryCategory category;

    @Column(nullable = false, length = 200)
    private String question;

    @Column(nullable = false, columnDefinition = "text")
    private String answer;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean visible;

    @Builder
    private Faq(InquiryCategory category, String question, String answer, int sortOrder, boolean visible) {
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.sortOrder = sortOrder;
        this.visible = visible;
    }

    /** 내용 교체 — 순서는 {@link #moveTo} 로만 바꾼다. */
    public void update(InquiryCategory category, String question, String answer, boolean visible) {
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.visible = visible;
    }

    public void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
