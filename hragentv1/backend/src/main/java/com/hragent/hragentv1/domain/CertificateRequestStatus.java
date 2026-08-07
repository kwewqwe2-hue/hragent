package com.hragent.hragentv1.domain;

public enum CertificateRequestStatus {
    PENDING_HR("待 HR 审核"),
    APPROVED("审核通过，待生成"),
    REJECTED("已驳回"),
    CANCELLED("已取消"),
    GENERATED("证明已生成"),
    GENERATION_FAILED("生成失败");

    private final String label;

    CertificateRequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
