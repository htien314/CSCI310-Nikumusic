
package com.example.trojanrideshare.service;

import java.time.Year;

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

        if (email == null ||
                !email.trim().matches("(?i)^[A-Z0-9._%+-]+@usc\\.edu$")) {
            return "Enter a valid @usc.edu email address.";
        }

        if (uscId == null || !uscId.matches("[0-9]{10}")) {
            return "USC ID must contain exactly 10 digits.";
        }

        if (password == null || password.length() < 6) {
            return "Password must be at least 6 characters.";
        }

        int currentYear = Year.now().getValue();

        if (graduationYear < currentYear ||
                graduationYear > currentYear + 6) {
            return "Graduation year must be between "
                    + currentYear + " and " + (currentYear + 6) + ".";
        }

        return null; // All validation checks passed.
    }
}
