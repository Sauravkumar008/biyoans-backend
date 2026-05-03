package com.biyoans.biyoans.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

@Entity
@Table(
        name = "byns_students",
        uniqueConstraints = @UniqueConstraint(columnNames = "email")
)
public class StudentOfBiyoans {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank                 // MANDATORY
    private String userName;  // full name

    @NotBlank
    private String qualification;

    @NotBlank
    private String fatherName;

    @Column(name = "role", nullable = false)
    private String role = "STUDENT";

    private String motherName;

    @Column(unique = true)
    private String aadharNumber;



    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dob;

    @Email
    @NotBlank                 // MANDATORY + UNIQUE (via table constraint)
    private String email;

    @NotBlank                 // MANDATORY
    private String whatsAppNumber;

    private String userPass;  // bcrypt hash

    @NotBlank
    private String gender;


    // New column for profile photo (you can save URL/path or filename)
    private String photoUrl;  // optional for now

    // getters & setters ...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }
    public String getFatherName() { return fatherName; }
    public void setFatherName(String fatherName) { this.fatherName = fatherName; }
    public String getMotherName() { return motherName; }
    public void setMotherName(String motherName) { this.motherName = motherName; }
    public String getAadharNumber() { return aadharNumber; }
    public void setAadharNumber(String aadharNumber) { this.aadharNumber = aadharNumber; }
    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { this.dob = dob; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getWhatsAppNumber() { return whatsAppNumber; }
    public void setWhatsAppNumber(String whatsAppNumber) { this.whatsAppNumber = whatsAppNumber; }
    public String getUserPass() { return userPass; }
    public void setUserPass(String userPass) { this.userPass = userPass; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
}
