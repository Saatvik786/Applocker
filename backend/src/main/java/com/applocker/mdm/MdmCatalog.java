package com.applocker.mdm;

import java.util.UUID;

public final class MdmCatalog {

    public static final UUID APPLOCKER_AREA = UUID.fromString("a1111111-1111-4111-8111-111111111111");
    public static final UUID STORE_APPS = UUID.fromString("a4444444-4444-4444-8444-444444444405");
    public static final UUID ENROLLED_DEVICES = UUID.fromString("a3333333-3333-4333-8333-333333333333");
    // Device prefix: a logged-on enroll opens an MDMFull session. A relative
    // ./Vendor URI is then evaluated as a user node, and AppLocker rejects it.
    public static final String APPLOCKER_BASE = "./Device/Vendor/MSFT/AppLocker";

    private MdmCatalog() {
    }
}
