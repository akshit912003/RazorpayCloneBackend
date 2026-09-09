package com.codingshuttle.razorpay.common.utils;

import java.security.BasicPermission;
import java.security.SecureRandom;
import java.util.Base64;

public class RandomizerUtil {
    public static final SecureRandom SECURE_RANDOM=new SecureRandom();

    public static String randomBase64(int length)
    {
        byte[] buf=new byte[length];
        SECURE_RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
