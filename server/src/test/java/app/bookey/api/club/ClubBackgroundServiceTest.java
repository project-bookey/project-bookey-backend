package app.bookey.api.club;

import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.ApiException;
import app.bookey.common.error.ErrorCode;
import app.bookey.common.storage.StorageService;
import app.bookey.common.support.RateLimiter;
import app.bookey.domain.club.Club;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 클럽 배경 사진 — 호스트만, 바꾸면 이전 파일을 지우고, 저장이 실패하면 방금 올린 파일을 지운다. */
class ClubBackgroundServiceTest {

    private static final long HOST = 1L, MEMBER = 2L, CLUB_ID = 10L;
    /** JPEG SOI + APP0 — 스니퍼가 형식만 판별하면 되는 최소 헤더. */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0};

    private final ClubService clubService = mock(ClubService.class);
    private final StorageService storage = mock(StorageService.class);
    private final BookeyProperties properties = new BookeyProperties(null, null, null, null, null, null, null, null,
            new BookeyProperties.Storage("local", null, null, null,
                    new BookeyProperties.Storage.Image(10_485_760, 10)), null);
    private final ClubBackgroundService service =
            new ClubBackgroundService(clubService, storage, mock(RateLimiter.class), properties);

    private static MockMultipartFile photo() {
        return new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG);
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((ApiException) e).getErrorCode();
    }

    @BeforeEach
    void setUp() {
        Club club = mock(Club.class);
        when(club.isHost(HOST)).thenReturn(true);
        when(clubService.getClub(CLUB_ID)).thenReturn(club);
        when(storage.enabled()).thenReturn(true);
        when(storage.store(anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenAnswer(inv -> "https://cdn/" + inv.getArgument(0));
    }

    @Test
    @DisplayName("호스트가 아니면 파일을 받기 전에 거절한다 — CLUB_NOT_HOST")
    void memberCannotUpload() {
        assertThatThrownBy(() -> service.upload(MEMBER, CLUB_ID, photo()))
                .extracting(ClubBackgroundServiceTest::codeOf)
                .isEqualTo(ErrorCode.CLUB_NOT_HOST);
        verify(storage, never()).store(anyString(), any(InputStream.class), anyLong(), anyString());
    }

    @Test
    @DisplayName("올리면 클럽 배경 경로에 저장하고, 이전 사진 파일은 지운다")
    void uploadReplacesAndDeletesPrevious() {
        when(clubService.changeBackground(eq(HOST), eq(CLUB_ID), anyString(), anyString())).thenReturn("old-key");

        service.upload(HOST, CLUB_ID, photo());

        verify(clubService).changeBackground(eq(HOST), eq(CLUB_ID),
                org.mockito.ArgumentMatchers.startsWith("https://cdn/clubs/10/background/"),
                org.mockito.ArgumentMatchers.startsWith("clubs/10/background/"));
        verify(storage).delete("old-key");
    }

    @Test
    @DisplayName("배경 저장이 실패하면 방금 올린 파일을 지운다")
    void failedSaveDeletesUploadedFile() {
        when(clubService.changeBackground(eq(HOST), eq(CLUB_ID), anyString(), anyString()))
                .thenThrow(new IllegalStateException("DB 끊김"));

        assertThatThrownBy(() -> service.upload(HOST, CLUB_ID, photo())).isInstanceOf(IllegalStateException.class);
        verify(storage).delete(org.mockito.ArgumentMatchers.startsWith("clubs/10/background/"));
    }

    @Test
    @DisplayName("빼면 배경을 비우고 이전 파일을 지운다")
    void removeClearsBackground() {
        when(clubService.changeBackground(HOST, CLUB_ID, null, null)).thenReturn("old-key");

        service.remove(HOST, CLUB_ID);

        verify(storage).delete("old-key");
    }
}
