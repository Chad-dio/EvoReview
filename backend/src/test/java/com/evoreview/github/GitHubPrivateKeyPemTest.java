package com.evoreview.github;

import org.junit.jupiter.api.Test;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubPrivateKeyPemTest {

    @Test
    void convertsPkcs1PemToParseablePkcs8() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        byte[] pkcs8 = pair.getPrivate().getEncoded();
        byte[] pkcs1 = unwrapPkcs8ToPkcs1(pkcs8);
        String pkcs1Pem = pem("RSA PRIVATE KEY", pkcs1);

        String pkcs8Pem = GitHubPrivateKeyPem.toPkcs8Pem(pkcs1Pem);
        byte[] converted = decodePemBody(pkcs8Pem, "PRIVATE KEY");
        KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(converted));
        assertTrue(pkcs8Pem.contains("BEGIN PRIVATE KEY"));
    }

    private static String pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }

    private static byte[] decodePemBody(String pem, String type) {
        String body = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(body);
    }

    private static byte[] unwrapPkcs8ToPkcs1(byte[] pkcs8) {
        int offset = 0;
        if (pkcs8[offset++] != 0x30) {
            throw new IllegalArgumentException("not a sequence");
        }
        offset += skipLength(pkcs8, offset);
        offset += skipTag(pkcs8, offset, 0x02);
        offset += skipTag(pkcs8, offset, 0x30);
        if (pkcs8[offset++] != 0x04) {
            throw new IllegalArgumentException("expected octet string");
        }
        int len = readLength(pkcs8, offset);
        offset += lengthBytes(pkcs8[offset]);
        byte[] pkcs1 = new byte[len];
        System.arraycopy(pkcs8, offset, pkcs1, 0, len);
        return pkcs1;
    }

    private static int skipTag(byte[] der, int offset, int tag) {
        if (der[offset] != (byte) tag) {
            throw new IllegalArgumentException("unexpected tag");
        }
        int len = readLength(der, offset + 1);
        return 1 + lengthBytes(der[offset + 1]) + len;
    }

    private static int skipLength(byte[] der, int offset) {
        return lengthBytes(der[offset]);
    }

    private static int readLength(byte[] der, int offset) {
        int first = der[offset] & 0xff;
        if (first < 128) {
            return first;
        }
        int count = first & 0x7f;
        int value = 0;
        for (int i = 1; i <= count; i++) {
            value = (value << 8) | (der[offset + i] & 0xff);
        }
        return value;
    }

    private static int lengthBytes(byte first) {
        int value = first & 0xff;
        if (value < 128) {
            return 1;
        }
        return 1 + (value & 0x7f);
    }
}
