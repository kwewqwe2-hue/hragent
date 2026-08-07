package com.hragent.hragentv1.domain;

public enum LeaveType {
    ANNUAL("年假"),
    SICK("病假"),
    PERSONAL("事假"),
    MARRIAGE("婚假");

    private final String label;

    LeaveType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
