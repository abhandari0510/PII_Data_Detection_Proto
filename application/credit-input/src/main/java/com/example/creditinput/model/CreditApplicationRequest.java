package com.example.creditinput.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreditApplicationRequest {

    private String name;
    private String phoneNumber;
    private String aadharNumber;
    private String panNumber;
    private String creditCardNumber;
    private String creditCardExpiry;
    private String cvc;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    @JsonProperty("phoneNumber")
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAadharNumber() {
        return aadharNumber;
    }

    @JsonProperty("aadharNumber")
    public void setAadharNumber(String aadharNumber) {
        this.aadharNumber = aadharNumber;
    }

    public String getPanNumber() {
        return panNumber;
    }

    @JsonProperty("panNumber")
    public void setPanNumber(String panNumber) {
        this.panNumber = panNumber;
    }

    public String getCreditCardNumber() {
        return creditCardNumber;
    }

    @JsonProperty("creditCardNumber")
    public void setCreditCardNumber(String creditCardNumber) {
        this.creditCardNumber = creditCardNumber;
    }

    public String getCreditCardExpiry() {
        return creditCardExpiry;
    }

    @JsonProperty("creditCardExpiry")
    public void setCreditCardExpiry(String creditCardExpiry) {
        this.creditCardExpiry = creditCardExpiry;
    }

    public String getCvc() {
        return cvc;
    }

    @JsonProperty("cvc")
    public void setCvc(String cvc) {
        this.cvc = cvc;
    }

    @Override
    public String toString() {
        return "CreditApplicationRequest{" +
                "name='" + name + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", aadharNumber='" + aadharNumber + '\'' +
                ", panNumber='" + panNumber + '\'' +
                ", creditCardNumber='" + creditCardNumber + '\'' +
                ", creditCardExpiry='" + creditCardExpiry + '\'' +
                ", cvc='" + cvc + '\'' +
                '}';
    }
}
