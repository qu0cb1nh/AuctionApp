import net.auctionapp.common.utils.ConfigReader;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ConsoleTestClient {
    public static void main(String[] args) {
        try (Socket socket = new Socket(ConfigReader.getServerHost(), ConfigReader.getServerPort())) {
            System.out.println("Connected to the Auction Server!");

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // 1. Background thread to LISTEN for messages from the Server (Broadcast)
            new Thread(() -> {
                try {
                    String serverMsg;
                    while ((serverMsg = in.readLine()) != null) {
                        System.out.println("\n[Server Sent]: " + serverMsg);
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from the Server.");
                }
            }).start();

            // 2. Main thread to SEND messages from the keyboard to the Server
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String myMsg = scanner.nextLine();
                out.println(myMsg);
            }

        } catch (IOException e) {
            System.out.println("Could not connect to the Server. Is the server running?");
        }
    }
}