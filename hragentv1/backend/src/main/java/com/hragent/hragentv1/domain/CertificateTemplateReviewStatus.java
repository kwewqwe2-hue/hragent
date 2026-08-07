package com.hragent.hragentv1.domain;

public enum CertificateTemplateReviewStatus {
    PENDING("待 HR 审核"),
    APPROVED("已通过"),
    REJECTED("已驳回"),
    CANCELLED("已取消");

    private final String label;

    CertificateTemplateReviewStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
