package app.bookey.common.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JoinCodeGeneratorTest {

    @RepeatedTest(50)
    @DisplayName("혼동 문자(O,0,I,1)가 없는 6자리 코드를 만든다")
    void generatesUnambiguousCode() {
        String code = JoinCodeGenerator.generate();

        assertThat(code).hasSize(6);
        assertThat(code).doesNotContain("O", "0", "I", "1");
        assertThat(JoinCodeGenerator.isValidFormat(code)).isTrue();
    }

    @Test
    @DisplayName("사용자가 소문자·하이픈을 섞어 입력해도 정규화한다")
    void normalizesUserInput() {
        assertThat(JoinCodeGenerator.normalize(" k7m-2qf ")).isEqualTo("K7M2QF");
    }

    @Test
    @DisplayName("형식이 어긋난 코드는 거부한다")
    void rejectsInvalidFormat() {
        assertThat(JoinCodeGenerator.isValidFormat("K7M2Q")).isFalse();     // 5자리
        assertThat(JoinCodeGenerator.isValidFormat("K7M2Q0")).isFalse();    // 0 포함
        assertThat(JoinCodeGenerator.isValidFormat(null)).isFalse();
    }
}
