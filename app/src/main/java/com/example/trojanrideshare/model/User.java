
package com.example.trojanrideshare.model;

public class User {

    private String uid;
    private String fullName;
    private String nickname;
    private String uscEmail;
    private String uscId;
    private int graduationYear;

    // Required by Firestore to convert documents into User objects.
    public User() {
    }

    public User(String uid, String fullName, String nickname,
                String uscEmail, String uscId, int graduationYear) {
        this.uid = uid;
        this.fullName = fullName;
        this.nickname = (nickname == null || nickname.trim().isEmpty())
                ? fullName : nickname;
        this.uscEmail = uscEmail;
        this.uscId = uscId;
        this.graduationYear = graduationYear;
    }

    public String getUid() {
        return uid;
    }

    public String getFullName() {
        return fullName;
    }

    public String getNickname() {
        return nickname;
    }

    public String getUscEmail() {
        return uscEmail;
    }

    public String getUscId() {
        return uscId;
    }

    public int getGraduationYear() {
        return graduationYear;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public void setUscEmail(String uscEmail) {
        this.uscEmail = uscEmail;
    }

    public void setUscId(String uscId) {
        this.uscId = uscId;
    }

    public void setGraduationYear(int graduationYear) {
        this.graduationYear = graduationYear;
    }
}
