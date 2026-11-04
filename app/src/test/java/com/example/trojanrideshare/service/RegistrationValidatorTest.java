package com.example.trojanrideshare.service;

import org.junit.Test;
import java.time.Year;
import static org.junit.Assert.*;

public class RegistrationValidatorTest {
    private String validate(String email, String id, int year) {
        return RegistrationValidator.validate("Hailey Tien", email, id, "password123", year);
    }
    @Test public void currentYearAndSixYearsLaterAreAllowed() {
        int year = Year.now().getValue();
        assertNull(validate("htien@usc.edu", "1234567890", year));
        assertNull(validate("htien@usc.edu", "1234567890", year + 6));
    }
    @Test public void yearsOutsideWindowAreRejected() {
        int year = Year.now().getValue();
        assertNotNull(validate("htien@usc.edu", "1234567890", year - 1));
        assertNotNull(validate("htien@usc.edu", "1234567890", year + 7));
    }
    @Test public void nonUscEmailIsRejected() {
        assertNotNull(validate("htien@example.com", "1234567890", Year.now().getValue()));
    }
    @Test public void idRequiresExactlyTenNumericDigits() {
        for (String id : new String[]{"123", "12345678901", "123456789x"})
            assertNotNull(validate("htien@usc.edu", id, Year.now().getValue()));
    }
    @Test public void fullNameAndPasswordAreRequired() {
        assertNotNull(RegistrationValidator.validate("", "htien@usc.edu", "1234567890", "password123", Year.now().getValue()));
        assertNotNull(RegistrationValidator.validate("Hailey", "htien@usc.edu", "1234567890", "", Year.now().getValue()));
    }
}
