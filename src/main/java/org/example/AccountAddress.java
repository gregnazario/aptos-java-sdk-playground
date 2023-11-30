package src.main.java.org.example;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static src.main.java.org.example.Utils.bytesToHex;

public class AccountAddress {
    private static final byte ED25519_SCHEME = 0;
    private final byte[] address;

    private AccountAddress(byte[] bytes) {
        this.address = bytes;
    }

    public static AccountAddress fromEd25519(Ed25519 ed25519Key) throws NoSuchAlgorithmException {
        byte[] publicKeyBytes = ed25519Key.publicKeyBytes();
        MessageDigest hasher = MessageDigest.getInstance("SHA3-256");
        hasher.update(publicKeyBytes);
        hasher.update(ED25519_SCHEME);
        return new AccountAddress(hasher.digest());
    }

    public byte[] getAddressBytes() {
        return this.address;
    }

    public String toString() {
        return "0x" + bytesToHex(this.getAddressBytes());
    }
}


