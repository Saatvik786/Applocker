package com.applocker.enrollment;

public class EnrollmentTokenNotFoundException extends RuntimeException {

    public EnrollmentTokenNotFoundException() {
        super("Enrollment token was not found");
    }
}
