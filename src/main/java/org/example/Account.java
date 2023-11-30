package src.main.java.org.example;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.*;
import java.util.HexFormat;

import static src.main.java.org.example.Utils.bytesToHex;

/**
 * Manages a key and associated address
 *
 * TODO: Manage sequence number and other info as well
 */
public class Account {
    private final Ed25519 ed25519;
    private final AccountAddress address;

    /**
     * Generates a new account, with an arbitrary private key
     *
     * @throws NoSuchAlgorithmException
     */
    public Account() throws NoSuchAlgorithmException {
        this.ed25519 = new Ed25519();
        this.address = AccountAddress.fromEd25519(ed25519);
    }

    /**
     * Funds the account with a given amount
     *
     * @param client
     * @param amount
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public String fundAccount(HttpClient client, int amount) throws IOException, InterruptedException {
        if (amount <= 0) {
            throw new RuntimeException("Invalid amount, must be greater than 0");
        }
        String fundJsonBody = String.format("{\"address\": \"%s\", \"amount\": %d}", this.address, amount);

        // Fund account
        HttpRequest fundRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://faucet.devnet.aptoslabs.com/fund"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(fundJsonBody))
                .build();
        HttpResponse<String> fundResponse = client.send(fundRequest, HttpResponse.BodyHandlers.ofString());
        assert (fundResponse.statusCode() == 200);

        // This is a big hack, but I can't seem to import Gson (I haven't used maven before)
        return fundResponse.body().split("\"")[3];
    }

    /**
     * Submits a transaction on behalf of the account
     *
     * @param client
     * @param sequenceNumber
     * @param maxGasAmount
     * @param gasUnitPrice
     * @param expirationTimestampSecs number of seconds from the epoch for the transaction to expire
     * @param payload                 String JSON representation of the payload
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws NoSuchAlgorithmException
     * @throws SignatureException
     * @throws InvalidKeyException
     */
    public String submitTransaction(HttpClient client, BigInteger sequenceNumber, BigInteger maxGasAmount, BigInteger gasUnitPrice, BigInteger expirationTimestampSecs, String payload) throws IOException, InterruptedException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        // Ensure a sequence number is provided
        if (sequenceNumber == null)
            throw new RuntimeException("Sequence number must be provided");

        // Ensure max gas amount is set
        if (maxGasAmount == null)
            maxGasAmount = BigInteger.valueOf(2000000);
        else if (maxGasAmount.compareTo(BigInteger.valueOf(2000000000)) > 0) {
            throw new RuntimeException("Max gas amount is too large");
        }

        // Ensure gas unit price is set
        if (gasUnitPrice == null) {
            gasUnitPrice = new BigInteger("100");
        } else if (maxGasAmount.compareTo(BigInteger.valueOf(2000000000)) > 0) {
            throw new RuntimeException("Gas unit price is too large");
        }

        // By default, wait 60 seconds
        if (expirationTimestampSecs == null) {
            // Now, + 60 seconds
            expirationTimestampSecs = BigInteger.valueOf(System.currentTimeMillis() / 1000 + 60);
        }

        // Build the raw transaction
        // TODO: Use a native JSON library
        String rawTransaction = String.format("{" +
                        "\"sender\": \"%s\", " +
                        "\"sequence_number\": \"%d\", " +
                        "\"max_gas_amount\": \"%d\", " +
                        "\"gas_unit_price\": \"%d\", " +
                        "\"expiration_timestamp_secs\":\"%d\", " +
                        "\"payload\": %s" +
                        "}",
                this.address,
                sequenceNumber,
                maxGasAmount,
                gasUnitPrice,
                expirationTimestampSecs,
                payload
        );

        // Go to the API to BCS encode it for signing
        HttpRequest encodeRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://fullnode.devnet.aptoslabs.com/v1/transactions/encode_submission"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(rawTransaction))
                .build();
        HttpResponse<String> signingMessageResponse = client.send(encodeRequest, HttpResponse.BodyHandlers.ofString());
        assert (signingMessageResponse.statusCode() == 200);

        // Sign the message, stripping of the 0x at the beginning of the string
        String signingMessageBody = signingMessageResponse.body();
        byte[] signingMessage = HexFormat.of().parseHex(signingMessageBody.substring(3, signingMessageBody.length() - 1));
        byte[] signature = this.ed25519.signBytes(signingMessage);

        // Combine the raw transaction with the authenticator (signature and public key)
        byte[] encodedPublicKey = this.ed25519.publicKeyBytes();
        String hexPublicKey = bytesToHex(encodedPublicKey);
        String signedTransaction = String.format("{" +
                        "\"sender\": \"%s\", " +
                        "\"sequence_number\": \"%d\", " +
                        "\"max_gas_amount\": \"%d\", " +
                        "\"gas_unit_price\": \"%d\", " +
                        "\"expiration_timestamp_secs\":\"%d\", " +
                        "\"payload\": %s, " +
                        "\"signature\": { " +
                        "\"type\":\"ed25519_signature\", " +
                        "\"public_key\":\"%s\", " +
                        "\"signature\":\"%s\"" +
                        "}" +
                        "}",
                this.address,
                sequenceNumber,
                maxGasAmount,
                gasUnitPrice,
                expirationTimestampSecs,
                payload,
                hexPublicKey,
                bytesToHex(signature)
        );

        // Submit the signed transaction
        HttpRequest submitRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://fullnode.devnet.aptoslabs.com/v1/transactions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(signedTransaction))
                .build();
        HttpResponse<String> pendingTxn = client.send(submitRequest, HttpResponse.BodyHandlers.ofString());
        assert (pendingTxn.statusCode() == 200);
        return pendingTxn.body();
    }

    /**
     * Waits on a transaction hash
     *
     * @param client
     * @param hash
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public static String waitForTransaction(HttpClient client, String hash) throws IOException, InterruptedException {
        for (int i = 0; i < 30; i++) {
            HttpRequest waitRequest = HttpRequest.newBuilder()
                    .uri(URI.create(String.format("https://fullnode.devnet.aptoslabs.com/v1/transactions/by_hash/0x%s", hash)))
                    .GET()
                    .build();
            HttpResponse<String> pendingTxn = client.send(waitRequest, HttpResponse.BodyHandlers.ofString());
            if (pendingTxn.statusCode() == 200) {
                return pendingTxn.body();
            }
            Thread.sleep(1000);
        }

        throw new RuntimeException("Failed to wait for transaction: " + hash);
    }
}
