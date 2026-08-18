package com.hragent.hragentv1.domain;

public enum CertificateLanguage {
    CHINESE("中文"),
    ENGLISH("英文"),
    BILINGUAL("中英双语");

    private final String label;

    CertificateLanguage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
