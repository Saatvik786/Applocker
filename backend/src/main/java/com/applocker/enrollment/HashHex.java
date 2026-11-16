package com.applocker.enrollment;

import java.util.HexFormat;

final class HashHex {

    private HashHex() {
    }

    static String toHex(byte[] value) {
        return HexFormat.of().formatHex(value);
    }

    static byte[] fromHex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
