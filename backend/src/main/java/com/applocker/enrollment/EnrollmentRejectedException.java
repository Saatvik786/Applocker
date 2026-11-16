package com.applocker.enrollment;

public class EnrollmentRejectedException extends RuntimeException {

    public EnrollmentRejectedException() {
        super("Enrollment rejected");
    }
}
