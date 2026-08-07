package com.hragent.hragentv1.domain;

public enum RequestStatus {
    PENDING_MANAGER("待主管审批"),
    PENDING_HR("待HR备案"),
    APPROVED("已通过"),
    REJECTED("已驳回");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
