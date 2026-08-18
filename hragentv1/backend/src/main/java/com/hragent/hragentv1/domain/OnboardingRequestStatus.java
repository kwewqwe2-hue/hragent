package com.hragent.hragentv1.domain;

public enum OnboardingRequestStatus {
    PENDING_HR("待 HR 审核"),
    APPROVED("审核通过"),
    REJECTED("已驳回");

    private final String label;

    OnboardingRequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
