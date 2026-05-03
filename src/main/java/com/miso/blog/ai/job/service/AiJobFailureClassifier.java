package com.miso.blog.ai.job.service;

import com.miso.blog.ai.job.code.AiJobFailureCode;
import com.miso.blog.common.code.ErrorCode;
import com.miso.blog.common.exception.GeneralException;
import com.miso.blog.common.security.SecretMaskingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiJobFailureClassifier {
    private final SecretMaskingService secretMaskingService;

    public AiJobFailure classify(Exception exception) {
        String detailMessage = secretMaskingService.mask(resolveDetailMessage(exception));
        String lowerMessage = detailMessage.toLowerCase();

        if (lowerMessage.contains("api key") && (lowerMessage.contains("설정") || lowerMessage.contains("missing"))) {
            return failure(AiJobFailureCode.OPENAI_API_KEY_MISSING, "OpenAI API Key가 설정되어 있지 않습니다.", detailMessage, false,
                    "관리자 설정에서 application-private.yml 또는 환경 변수의 OpenAI API Key를 확인하세요.");
        }
        if (lowerMessage.contains("status=401") || lowerMessage.contains("status=403")) {
            return failure(AiJobFailureCode.OPENAI_AUTH_FAILED, "OpenAI 인증에 실패했습니다.", detailMessage, false,
                    "API Key가 유효한지, 프로젝트 권한과 결제 상태가 정상인지 확인하세요.");
        }
        if (lowerMessage.contains("status=429") || lowerMessage.contains("rate limit")) {
            return failure(AiJobFailureCode.OPENAI_RATE_LIMIT, "OpenAI 요청 한도를 초과했습니다.", detailMessage, true,
                    "잠시 후 다시 시도하거나 모델, 요청량, 결제 한도를 확인하세요.");
        }
        if (lowerMessage.contains("timeout") || lowerMessage.contains("timed out") || detailMessage.contains("중단")) {
            return failure(AiJobFailureCode.OPENAI_TIMEOUT, "AI 요청 시간이 초과되었거나 중단되었습니다.", detailMessage, true,
                    "잠시 후 재시도하세요. 반복되면 글 길이나 수정 라운드를 줄여보세요.");
        }
        if (detailMessage.contains("네트워크") || lowerMessage.contains("io_error") || lowerMessage.contains("connection")) {
            return failure(AiJobFailureCode.OPENAI_NETWORK_ERROR, "AI 요청 중 네트워크 오류가 발생했습니다.", detailMessage, true,
                    "네트워크 상태를 확인한 뒤 재시도하세요.");
        }
        if (detailMessage.contains("응답을 해석하지 못") || lowerMessage.contains("parse")) {
            return failure(AiJobFailureCode.OPENAI_BAD_RESPONSE, "AI 응답 형식이 올바르지 않습니다.", detailMessage, true,
                    "재시도하면 해결될 수 있습니다. 반복되면 프롬프트나 출력 형식을 점검하세요.");
        }
        if (lowerMessage.contains("status=400") || detailMessage.contains("OpenAI")) {
            return failure(AiJobFailureCode.OPENAI_BAD_REQUEST, "OpenAI 요청이 거절되었습니다.", detailMessage, false,
                    "요청 본문, 모델명, 입력 길이를 확인하세요.");
        }
        if (exception instanceof GeneralException generalException) {
            ErrorCode errorCode = generalException.getErrorCode();
            if (errorCode == ErrorCode.NOT_FOUND) {
                return failure(AiJobFailureCode.TARGET_NOT_FOUND, "대상 데이터를 찾을 수 없습니다.", detailMessage, false,
                        "글이나 분석 결과가 삭제되었는지 확인하고 화면을 새로고침하세요.");
            }
            if (errorCode == ErrorCode.CONFLICT) {
                return failure(AiJobFailureCode.CONFLICT_STATE, "현재 상태에서는 작업을 진행할 수 없습니다.", detailMessage, false,
                        "글 상태를 확인한 뒤 초안 또는 검수 대기 상태에서 다시 시도하세요.");
            }
            if (errorCode == ErrorCode.BAD_REQUEST) {
                return failure(AiJobFailureCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.", detailMessage, false,
                        "입력값, 글 길이, 필수 항목을 확인하세요.");
            }
        }

        return failure(AiJobFailureCode.UNKNOWN_ERROR, "알 수 없는 오류로 AI 작업이 실패했습니다.", detailMessage, true,
                "잠시 후 재시도하세요. 반복되면 개발자용 상세 메시지를 확인하세요.");
    }

    private AiJobFailure failure(
            AiJobFailureCode code,
            String message,
            String detailMessage,
            boolean retryable,
            String actionGuide
    ) {
        return new AiJobFailure(code, message, detailMessage, retryable, actionGuide);
    }

    private String resolveDetailMessage(Exception exception) {
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return exception.getClass().getSimpleName();
    }
}
