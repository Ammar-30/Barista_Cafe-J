import java.io.*;
import java.net.*;

public class Customer {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 8888;

    private static BufferedReader in;
    private static PrintWriter out;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {

            // Communication streams Setup
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Thread to handle incoming server message
            new Thread(() -> listenForServerMessages()).start();

            // Displaying welcome message
            System.out.println(in.readLine());

            // Send the client's name to the server
            out.println(userInput.readLine());

            // Handling user input
            handleUserInput(userInput);

        } catch (IOException e) {
            System.err.println("Error connecting to the server");
        }
    }
    private static void handleUserInput(BufferedReader userInput) throws IOException {
        String command;
        while ((command = userInput.readLine()) != null) {
            command = command.trim().toLowerCase();

            if ("exit".equals(command)) {
                out.println(command);  // Inform server that client is exiting
                break;
            }

            if (command.equals("order status") || command.equals("collect") || command.startsWith("order")) {
                out.println(command);  // Sending command to the server
            } else {
                System.out.println("Invalid comand. Please try again.");
            }
        }
        closeConnection();
    }
    private static void listenForServerMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println(message);  // Display any server message
            }
        } catch (IOException e) {
            System.out.println("Connection closed by client!");
        }
    }
    private static void closeConnection() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            System.out.println("GoodBye!");
        } catch (IOException e) {
            System.err.println("Error closing resources.");
        }
    }
}
