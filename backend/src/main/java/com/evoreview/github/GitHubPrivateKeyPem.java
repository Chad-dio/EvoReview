package com.evoreview.github;

import java.util.Base64;

final class GitHubPrivateKeyPem {

    private GitHubPrivateKeyPem() {
    }

    static String toPkcs8Pem(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("GitHub App private key is empty");
        }
        String normalized = pem.replace("\r\n", "\n").trim();
        if (normalized.contains("BEGIN PRIVATE KEY")) {
            return normalized + "\n";
        }
        if (!normalized.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalArgumentException("GitHub App private key must be a PEM file");
        }
        byte[] pkcs1 = decodePemBody(normalized, "RSA PRIVATE KEY");
        byte[] pkcs8 = wrapPkcs1InPkcs8(pkcs1);
        return encodePem("PRIVATE KEY", pkcs8);
    }

    private static byte[] decodePemBody(String pem, String type) {
        String body = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(body);
    }

    private static String encodePem(String type, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }

    private static byte[] wrapPkcs1InPkcs8(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] algorithm = {
                0x30, 0x0d,
                0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
                0x05, 0x00
        };
        byte[] octetString = concat(new byte[]{0x04}, encodeDerLength(pkcs1.length), pkcs1);
        byte[] body = concat(version, algorithm, octetString);
        return concat(new byte[]{0x30}, encodeDerLength(body.length), body);
    }

    private static byte[] encodeDerLength(int length) {
        if (length < 128) {
            return new byte[]{(byte) length};
        }
        if (length <= 0xff) {
            return new byte[]{(byte) 0x81, (byte) length};
        }
        return new byte[]{(byte) 0x82, (byte) ((length >> 8) & 0xff), (byte) (length & 0xff)};
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }
}
