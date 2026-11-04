
package com.example.trojanrideshare.service;

import java.time.Year;
import java.time.ZoneOffset;

public class RegistrationValidator {

    public static String validate(
            String fullName,
            String email,
            String uscId,
            String password,
            int graduationYear) {

        if (fullName == null || fullName.trim().isEmpty()) {
            return "Full name is required.";
        }
        if (fullName.trim().length() > 100) {
            return "Full name must be at most 100 characters.";
        }

        if (email == null ||
                email.trim().length() > 254 || !email.trim().matches("(?i)^[A-Z0-9._%+-]+@usc\\.edu$")) {
            return "Enter a valid @usc.edu email address.";
        }

        if (uscId == null || !uscId.matches("[0-9]{10}")) {
            return "USC ID must contain exactly 10 digits.";
        }

        if (password == null || password.length() < 6) {
            return "Password must be at least 6 characters.";
        }

        int currentYear = Year.now(ZoneOffset.UTC).getValue();

        if (graduationYear < currentYear ||
                graduationYear > currentYear + 6) {
            return "Graduation year must be between "
                    + currentYear + " and " + (currentYear + 6) + ".";
        }

        return null; // All validation checks passed.
    }

    public static String validateNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) return "Nickname cannot be empty.";
        return nickname.trim().length() > 100 ? "Nickname must be at most 100 characters." : null;
    }
}
