package com.sparta.ditto.feed.application;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.feed.application.dto.command.UploadUrlCommand;
import com.sparta.ditto.feed.application.dto.command.UploadUrlCommand.FileItem;
import com.sparta.ditto.feed.application.dto.result.UploadUrlResult;
import com.sparta.ditto.feed.application.service.UploadUrlService;
import com.sparta.ditto.feed.application.port.S3Port;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class UploadUrlServiceTest {

    @Mock
    private S3Port s3Port;

    @InjectMocks
    private UploadUrlService uploadUrlService;

    @BeforeEach
    void setUp() {
        lenient().when(s3Port.generatePresignedPutUrl(anyString(), anyString()))
                .thenReturn("https://test-bucket.s3.amazonaws.com/feeds/test-key");
    }

    private UploadUrlResult call(List<FileItem> files) {
        return uploadUrlService.generateUploadUrls(new UploadUrlCommand(files));
    }

    @Test
    @DisplayName("TC-001-1: image/jpeg 10MB 이하 → 200, presignedUrl과 s3Key 반환")
    void tc001_1_jpeg이미지_정상_presignedUrl_반환() {
        UploadUrlResult result = call(List.of(new FileItem("photo.jpg", "image/jpeg", 5_242_880L)));

        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).s3Key()).startsWith("feeds/").endsWith(".jpg");
        assertThat(result.files().get(0).presignedUrl()).isNotBlank();
    }

    @Test
    @DisplayName("TC-001-2: video/mp4 170MB 이하 → 200, presignedUrl과 s3Key 반환")
    void tc001_2_mp4영상_정상_presignedUrl_반환() {
        UploadUrlResult result = call(List.of(new FileItem("clip.mp4", "video/mp4", 100_000_000L)));

        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).s3Key()).startsWith("feeds/").endsWith(".mp4");
        assertThat(result.files().get(0).presignedUrl()).isNotBlank();
    }

    @Test
    @DisplayName("TC-001-3: 이미지 5장 → 200, 파일 5개 URL 반환")
    void tc001_3_이미지5장_정상_URL5개_반환() {
        UploadUrlResult result = call(List.of(
                new FileItem("1.jpg", "image/jpeg", 1_000_000L),
                new FileItem("2.jpg", "image/jpeg", 1_000_000L),
                new FileItem("3.jpg", "image/jpeg", 1_000_000L),
                new FileItem("4.jpg", "image/jpeg", 1_000_000L),
                new FileItem("5.jpg", "image/jpeg", 1_000_000L)
        ));

        assertThat(result.files()).hasSize(5);
    }

    @Test
    @DisplayName("TC-001-4: 이미지 6장 → 400, VALIDATION_ERROR 이미지는 최대 5장까지 업로드할 수 있습니다.")
    void tc001_4_이미지6장_초과_예외() {
        assertThatThrownBy(() -> call(List.of(
                new FileItem("1.jpg", "image/jpeg", 1_000_000L),
                new FileItem("2.jpg", "image/jpeg", 1_000_000L),
                new FileItem("3.jpg", "image/jpeg", 1_000_000L),
                new FileItem("4.jpg", "image/jpeg", 1_000_000L),
                new FileItem("5.jpg", "image/jpeg", 1_000_000L),
                new FileItem("6.jpg", "image/jpeg", 1_000_000L)
        )))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("이미지는 최대 5장까지 업로드할 수 있습니다.");
                });
    }

    @Test
    @DisplayName("TC-001-5: 영상 2개 → 400, VALIDATION_ERROR 영상은 최대 1개까지 업로드할 수 있습니다.")
    void tc001_5_영상2개_초과_예외() {
        assertThatThrownBy(() -> call(List.of(
                new FileItem("a.mp4", "video/mp4", 10_000_000L),
                new FileItem("b.mp4", "video/mp4", 10_000_000L)
        )))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("영상은 최대 1개까지 업로드할 수 있습니다.");
                });
    }

    @Test
    @DisplayName("TC-001-6: 이미지 5장 + 영상 1개 → 200, 파일 6개 URL 반환")
    void tc001_6_이미지5영상1_정상_URL6개_반환() {
        UploadUrlResult result = call(List.of(
                new FileItem("1.jpg", "image/jpeg", 1_000_000L),
                new FileItem("2.jpg", "image/jpeg", 1_000_000L),
                new FileItem("3.jpg", "image/jpeg", 1_000_000L),
                new FileItem("4.jpg", "image/jpeg", 1_000_000L),
                new FileItem("5.jpg", "image/jpeg", 1_000_000L),
                new FileItem("v.mp4", "video/mp4", 10_000_000L)
        ));

        assertThat(result.files()).hasSize(6);
    }

    @Test
    @DisplayName("TC-001-7: 합산 7개 → 400, VALIDATION_ERROR 이미지와 영상을 합쳐 최대 6개까지 업로드할 수 있습니다.")
    void tc001_7_합산7개_초과_예외() {
        List<FileItem> files = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            files.add(new FileItem("img" + i + ".jpg", "image/jpeg", 1_000_000L));
        }
        files.add(new FileItem("v1.mp4", "video/mp4", 1_000_000L));
        files.add(new FileItem("v2.mp4", "video/mp4", 1_000_000L));

        assertThatThrownBy(() -> call(files))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("이미지와 영상을 합쳐 최대 6개까지 업로드할 수 있습니다.");
                });
    }

    @Test
    @DisplayName("TC-001-8: application/pdf → 400, INVALID_MEDIA_TYPE 허용되지 않은 파일 형식입니다.")
    void tc001_8_pdf_허용외타입_예외() {
        assertThatThrownBy(() -> call(List.of(new FileItem("doc.pdf", "application/pdf", 1_000_000L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("INVALID_MEDIA_TYPE");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("허용되지 않은 파일 형식입니다.");
                });
    }

    @Test
    @DisplayName("TC-001-9: 이미지 10MB 초과 → 400, MEDIA_SIZE_EXCEEDED 이미지는 장당 10MB 이하로 업로드해주세요.")
    void tc001_9_이미지용량초과_예외() {
        assertThatThrownBy(() -> call(List.of(new FileItem("big.jpg", "image/jpeg", 10_485_761L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("MEDIA_SIZE_EXCEEDED");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("이미지는 장당 10MB 이하로 업로드해주세요.");
                });
    }

    @Test
    @DisplayName("TC-001-10: 영상 170MB 초과 → 400, MEDIA_SIZE_EXCEEDED 영상은 170MB 이하로 업로드해주세요.")
    void tc001_10_영상용량초과_예외() {
        assertThatThrownBy(() -> call(List.of(new FileItem("big.mp4", "video/mp4", 178_257_921L))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("MEDIA_SIZE_EXCEEDED");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("영상은 170MB 이하로 업로드해주세요.");
                });
    }

    @Test
    @DisplayName("TC-001-11: files 빈 배열 → 400, VALIDATION_ERROR 업로드할 파일을 선택해주세요.")
    void tc001_11_빈배열_예외() {
        assertThatThrownBy(() -> call(List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(be.getErrorCode().getMessage()).isEqualTo("업로드할 파일을 선택해주세요.");
                });
    }

    @Test
    @DisplayName("TC-001-12: fileName 확장자와 fileType 불일치 → fileType 기준 확장자로 s3Key 생성")
    void tc001_12_파일명확장자불일치_fileType기준_s3Key생성() {
        UploadUrlResult result = call(List.of(new FileItem("image.png", "image/jpeg", 1_000_000L)));

        assertThat(result.files().get(0).s3Key()).endsWith(".jpg");
    }

    @Test
    @DisplayName("TC-001-13: 정상 요청 → s3Key는 feeds/{UUID}.{ext} 형식, presignedUrl은 S3Port 반환값")
    void tc001_13_s3Key형식과_presignedUrl_검증() {
        UploadUrlResult result = call(List.of(new FileItem("test.jpg", "image/jpeg", 1_000_000L)));

        assertThat(result.files().get(0).s3Key())
                .matches("feeds/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg");
        assertThat(result.files().get(0).presignedUrl())
                .isEqualTo("https://test-bucket.s3.amazonaws.com/feeds/test-key");
    }
}
