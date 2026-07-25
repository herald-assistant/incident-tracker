package pl.mkn.tdw.features.changeverification.job.api;

import java.util.List;

public record ChangeVerificationSmokeHttpResultResponse(
        String method,
        String url,
        Integer statusCode,
        long durationMillis,
        String bodyExcerpt,
        List<ChangeVerificationNameValueResponse> headers,
        String errorMessage
) {

    public ChangeVerificationSmokeHttpResultResponse {
        headers = headers != null ? List.copyOf(headers) : List.of();
    }
}
