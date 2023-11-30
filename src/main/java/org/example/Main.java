package src.main.java.org.example;

import java.io.IOException;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.security.*;

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {

    public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, IOException, InterruptedException {

        HttpClient client = HttpClient.newBuilder().build();
        Account account = new Account();

        // Fund the account
        String fundHash = account.fundAccount(client, 1000000000);
        String fundResponse = Account.waitForTransaction(client, fundHash);
        System.out.println("Committed Fund Transaction: " + fundResponse);

        // Transfer 100 octas to 0x12345
        String response = account.submitTransaction(client, BigInteger.ZERO, BigInteger.valueOf(10000), null, null,
                "{\"type\": \"entry_function_payload\", \"function\": \"0x1::aptos_account::transfer\",\"type_arguments\": [],\"arguments\": [ \"0x12345\", \"100\" ]}");
        System.out.println("Transaction Submitted:" + response);
        String finalResponse = Account.waitForTransaction(client, fundHash);
        System.out.println("Committed Transaction: " + finalResponse);
    }
}