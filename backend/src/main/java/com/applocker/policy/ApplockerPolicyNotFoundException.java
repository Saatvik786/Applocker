package com.applocker.policy;

public class ApplockerPolicyNotFoundException extends RuntimeException {

    public ApplockerPolicyNotFoundException() {
        super("AppLocker policy was not found");
    }
}
