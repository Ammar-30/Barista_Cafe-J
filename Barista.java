import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import Helpers.*;

public class Barista {
    private static final int PORT = 8888; // Port number for the server (should be same for both client and server in order to establish succesful connection)
    // Shared lists for managing orders at different stages
    private static final List<OrderItem> waitingArea = Collections.synchronizedList(new ArrayList<>());
    private static final List<OrderItem> brewingArea = Collections.synchronizedList(new ArrayList<>());
    private static final List<OrderItem> trayArea = Collections.synchronizedList(new ArrayList<>());
    // Maps to store client-specific data
    private static final Map<String, PrintWriter> clientOutputStreams = new ConcurrentHashMap<>();
    private static final Map<String, ClientOrder> clientOrders = new ConcurrentHashMap<>();
    // tracking active client threads
    private static final Set<Thread> activeClientThreads = new HashSet<>();
    // Thread pool for processing orders from customers
    private static final ExecutorService orderProcessingPool = Executors.newFixedThreadPool(4); // Thread pool for order processing

    public static void main(String[] args) {
        System.out.println("---- Barista log ---- Starting server...");

        // handle server shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("---- Barista log ---- Server is shutting down...");
            closeServer();
        }));
        // Start the server and accept client connections
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Barista Server running...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread clientThread = new Thread(new ClientHandler(clientSocket));
                activeClientThreads.add(clientThread); // Track client threads
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("Error starting the Barista server!");
        }
    }
    // Handles client connections
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private String clientName;
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
                out.println("Welcome! Please enter your name -->");
                clientName = in.readLine();
                if (clientName == null || clientName.isBlank()) {
                    socket.close(); // Close the connection if name is invalid of customer
                    return;
                }
                out.println("Welcome " + clientName + "! What would you like to order today?");

                // Store the output stream for this client
                clientOutputStreams.put(clientName, out);
                clientOrders.put(clientName, new ClientOrder(waitingArea, brewingArea, trayArea));
                logState();

                // Read and process commands from the customer
                String command;
                while ((command = in.readLine()) != null) {
                    command = command.trim().toLowerCase();

                    if (command.startsWith("order") && !command.equals("order status")) {
                        handleOrder(command, out);  // Processing order commands like "order 1 tea or 2 coffee" maybe
                    } else if (command.equals("order status")) {
                        handleOrderStatus(out);  // Processing order status command
                    } else if (command.equals("collect")) {
                        handleCollect(out);  // Processing collect command
                    } else if (command.equals("exit")) {
                        handleExit(out);  // Processing exit command
                        break;
                    } else {
                        out.println("User Command Unknown. Try again Please!");
                    }
                }
            } catch (IOException e) {
                System.err.println("Error handling client ");
            } finally {
                // Cleanup when client disconnects
                if (clientName != null) {
                    clientOrders.remove(clientName);         // Remove client orders
                    clientOutputStreams.remove(clientName);  // Remove output stream for the client
                }
                logState();  // Log the updated state
            }
        }
        // Handle order commands
        private void handleOrder(String command, PrintWriter out) {
            String orderDetails = command.replaceFirst("order", "").trim();
            if (orderDetails.isEmpty()) {
                out.println("Error! Invalid order format. Please retry.");
                return;
            }
            String[] items = orderDetails.split("and"); // for Multiple orders from same customer
            for (String item : items) {
                item = item.trim();
                String[] parts = item.split(" ");
                try {
                    int quantity = Integer.parseInt(parts[0]);
                    String type = parts[1].toLowerCase(); // Tea or coffee (tea or coffee)
                    if (!type.equals("tea") && !type.equals("coffee")) {
                        out.println("Error! Invalid item type. Only 'tea' or 'coffee' are allowed.");
                        return;
                    }
                    // Adding item to the waiting area
                    for (int i = 0; i < quantity; i++) {
                        waitingArea.add(new OrderItem(type, clientName));
                    }
                } catch (Exception e) {
                    out.println("Error! Invalid order format. Please retry.");
                    return;
                }
            }
            out.println("Order received for " + clientName + " (" + orderDetails + ")");
            logState();
            processOrders(); // Start processing orders
        }
        // Handling order status command
        private void handleOrderStatus(PrintWriter out) {
            // Check if the client is idle
            boolean isIdle = waitingArea.stream().noneMatch(item -> item.getClientName().equals(clientName)) &&
                    brewingArea.stream().noneMatch(item -> item.getClientName().equals(clientName)) &&
                    trayArea.stream().noneMatch(item -> item.getClientName().equals(clientName));

            if (isIdle) {
                out.println("No order found for " + clientName);
                return;
            }

            // Counting items in each area for the client
            long waitingItems = waitingArea.stream()
                    .filter(item -> item.getClientName().equals(clientName))
                    .count();
            long brewingItems = brewingArea.stream()
                    .filter(item -> item.getClientName().equals(clientName))
                    .count();
            long trayItems = trayArea.stream()
                    .filter(item -> item.getClientName().equals(clientName))
                    .count();
            // Displaying order status
            out.println("Order status for " + clientName + ":");
            out.println("--> " + waitingItems + " items in the waiting area");
            out.println("--> " + brewingItems + " items being prepared");
            out.println("--> " + trayItems + " items in the tray");

            if (trayItems > 0) {
                out.println("Your order is ready to collect!");
            }
        }
        // Handling collect command from customer
        private void handleCollect(PrintWriter out) {
            // Check if the order is complete and items are in the tray
            long readyItems = trayArea.stream()
                    .filter(item -> item.getClientName().equals(clientName))
                    .count();

            if (readyItems > 0) {
                out.println("Order collected! Thank you " + clientName + " - Hope to see you again soon!");
                // Remove items from tray after collection
                trayArea.removeIf(item -> item.getClientName().equals(clientName));
            } else {
                out.println("We are still brewing up love for you ;) - Please check the status again in a bit.");
            }
            logState();
        }
        // Handing exit command from client
        private void handleExit(PrintWriter out) {
            out.println("Exitting the cafe");
            ClientOrder order = clientOrders.remove(clientName);
            if (order != null) {
                // Remove client orders from all areas
                waitingArea.removeIf(item -> item.getClientName().equals(clientName));
                brewingArea.removeIf(item -> item.getClientName().equals(clientName));
                trayArea.removeIf(item -> item.getClientName().equals(clientName));
            }
            logState();
        }
        // Processing the orders using threads
        private void processOrders() {
            orderProcessingPool.submit(() -> {
                while (brewingArea.size() < 4 && !waitingArea.isEmpty()) {
                    OrderItem item = waitingArea.remove(0);
                    brewingArea.add(item);

                    try {
                        // Brewing time (tea 30s, coffee 45s)
                        Thread.sleep(item.getType().equals("tea") ? 30000 : 45000);

                        synchronized (trayArea) {
                            trayArea.add(item); // Moving item to the tray
                            brewingArea.remove(item);

                            // Checking if all orders for this client are in the tray & notify
                            String clientName = item.getClientName();
                            boolean isComplete = waitingArea.stream().noneMatch(i -> i.getClientName().equals(clientName)) &&
                                    brewingArea.stream().noneMatch(i -> i.getClientName().equals(clientName));

                            if (isComplete) {
                                PrintWriter clientOut = clientOutputStreams.get(clientName);
                                if (clientOut != null) {
                                    clientOut.println(clientName + " - Your order is ready to be collected. Thank you!");
                                }
                                System.out.println("Order for " + clientName + " is ready to be collected."); // on server
                            }
                        }
                        logState();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
        // The current state of the system (refrehses everytime)
        private static void logState() {
            System.out.println("---- Barista Order log ----");
            System.out.println("Number of clients in cafe: " + clientOrders.size());
            System.out.println("Number of clients waiting for orders: " + clientOrders.values().stream().filter(order -> !order.isIdle()).count());
            System.out.println("Waiting area: " + waitingArea.size());
            System.out.println("Brewing area: " + brewingArea.size());
            System.out.println("Tray area: " + trayArea.size());
            System.out.println("---------------------------");
        }
    }
    private static void closeServer() {
        // shutting down all threads and resources ( bonus feature )
        orderProcessingPool.shutdownNow();
        activeClientThreads.forEach(Thread::interrupt);
        try {
            Thread.sleep(1000);  // Waiting for threads to finish
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
