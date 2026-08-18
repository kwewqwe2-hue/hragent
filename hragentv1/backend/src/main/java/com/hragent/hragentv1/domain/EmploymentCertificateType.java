package com.hragent.hragentv1.domain;

public enum EmploymentCertificateType {
    STANDARD("标准在职证明"),
    VISA("出境/签证在职证明");

    private final String label;

    EmploymentCertificateType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
