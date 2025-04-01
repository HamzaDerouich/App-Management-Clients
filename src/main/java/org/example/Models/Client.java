package org.example.Models;

public class Client {
    private int id;
    private String firstName;
    private String lastName;
    private String companyName;
    private String email;
    private String address1;
    private String country;
    private String phoneNumber;
    private int clientGroupId;
    private String creationDate;
    private String notes;

    // Constructor
    public Client(int id, String firstName, String lastName, String companyName, String email, String address1, String country, String phoneNumber, int clientGroupId, String creationDate, String notes) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.companyName = companyName;
        this.email = email;
        this.address1 = address1;
        this.country = country;
        this.phoneNumber = phoneNumber;
        this.clientGroupId = clientGroupId;
        this.creationDate = creationDate;
        this.notes = notes;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress1() {
        return address1;
    }

    public void setAddress1(String address1) {
        this.address1 = address1;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public int getClientGroupId() {
        return clientGroupId;
    }

    public void setClientGroupId(int clientGroupId) {
        this.clientGroupId = clientGroupId;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "Client{" +
                "id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", companyName='" + companyName + '\'' +
                ", email='" + email + '\'' +
                ", address1='" + address1 + '\'' +
                ", country='" + country + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", clientGroupId=" + clientGroupId +
                ", creationDate='" + creationDate + '\'' +
                ", notes='" + notes + '\'' +
                '}';
    }
}
