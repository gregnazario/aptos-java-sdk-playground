package src.main.java.org.example;

import java.security.*;
import java.util.Arrays;

public class Ed25519 {
    private final KeyPair keyPair;

    /**
     * Generates a new private key
     * TODO: Allow loading an existing private key
     *
     * @throws NoSuchAlgorithmException
     */
    public Ed25519() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        this.keyPair = kpg.generateKeyPair();
    }

    /**
     * Signs arbitrary bytes with the private key
     *
     * @param bytesToSign
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws SignatureException
     */
    public byte[] signBytes(byte[] bytesToSign) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signing = Signature.getInstance("Ed25519");
        signing.initSign(this.keyPair.getPrivate());
        signing.update(bytesToSign);
        return signing.sign();
    }

    /**
     * Returns the raw public keys bytes for signature purposes
     *
     * @return
     */
    public byte[] publicKeyBytes() {
        byte[] encodedKey = this.keyPair.getPublic().getEncoded();
        return Arrays.copyOfRange(encodedKey,
                12, encodedKey.length);
    }
}
