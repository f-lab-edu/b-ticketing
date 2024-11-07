package com.bticketing.appqueue.util;

import java.util.UUID;

public class TokenUtil {
    public static String generateUserToken() {
        return UUID.randomUUID().toString();
    }
}
