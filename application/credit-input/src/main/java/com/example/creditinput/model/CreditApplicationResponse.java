package com.example.creditinput.model;

public class CreditApplicationResponse {

    private final String applicationNumber;

    public CreditApplicationResponse(String applicationNumber) {
        this.applicationNumber = applicationNumber;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }
}
