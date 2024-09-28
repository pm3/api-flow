package eu.aston.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.HexFormat;

public class Hash {

    public static String sha1(byte[] data) {
        return hash(data, "SHA-1");
    }

    public static String sha2(byte[] data) {
        return hash(data, "SHA-256");
    }

    public static String hash(byte[] data, String alg) {
        String s2;
        try {
            MessageDigest digest = MessageDigest.getInstance(alg);
            s2 = HexFormat.of().formatHex(digest.digest(data)).toLowerCase();
        } catch (Exception e) {
            throw new SecurityException(e);
        }
        return s2;
    }

    public static String hmacSha1(byte[] data, byte[] keyBytes) {
        String s2;
        try{
            SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
            s2 = HexFormat.of().formatHex(mac.doFinal(data)).toLowerCase();
        }catch (Exception e){
            throw new SecurityException(e);
        }
        return s2;
    }
}
