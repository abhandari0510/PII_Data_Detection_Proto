package com.example.creditinput.model;

public class PolicyResponse {

    private String applicationNumber;

    public PolicyResponse() {
    }

    public PolicyResponse(String applicationNumber) {
        this.applicationNumber = applicationNumber;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public void setApplicationNumber(String applicationNumber) {
        this.applicationNumber = applicationNumber;
    }
}
