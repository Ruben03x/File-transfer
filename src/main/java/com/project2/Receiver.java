package com.project2;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Represents the receiver side of a TCP and potentially RBUDP file transfer.
 */
public class Receiver {
    private volatile static ServerSocket serverSocket;
    private volatile Socket socket;
    private volatile GUI_Receiver guiReceiver;
    private volatile int UDPPort;
    private volatile DatagramSocket datagramSocket;
    private volatile BufferedWriter bufWrite;
    private volatile BufferedReader bufRead;

    // Default directory for storing received files.
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + File.separator + "receivedFiles";

    /**
     * Constructs a new Receiver with the specified GUI interface.
     *
     * @param guiReceiver the GUI interface associated with this Receiver.
     */
    public Receiver(GUI_Receiver guiReceiver) {
        this.guiReceiver = guiReceiver;
    }

    /**
     * Attempts to start a TCP connection on the specified port.
     *
     * @param port the port number to bind the TCP server socket.
     * @return true if the TCP connection was successfully established, false
     *         otherwise.
     */
    public Boolean startTCPConnection(int port) {
        try {
            serverSocket = new ServerSocket(port);
            waitForSender();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            closeResources();
            return false;
        }
    }

    /**
     * Waits for an incoming connection from a sender and starts listening for data.
     */
    public void waitForSender() {
        new Thread(() -> {
            try {
                socket = serverSocket.accept();
                bufWrite = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                bufRead = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                guiReceiver.showDialog("Sender Connected");
                setupUDPConnection();
                waitForSending();
            } catch (IOException e) {
                e.printStackTrace();
                // closeResources();
            }
        }).start();
    }

    /**
     * Sets up UDP connection by assigning a UDP port.
     */
    private void setupUDPConnection() {
        UDPPort = 4000;
        while (true) {
            try {
                datagramSocket = new DatagramSocket(UDPPort);
                break;
            } catch (IOException e) {
                UDPPort++; // Increment UDP port if the current one is already in use.
            }
        }
        try {
            bufWrite.write(Integer.toString(UDPPort));
            bufWrite.newLine();
            bufWrite.flush();
        } catch (IOException e) {
            closeResources(); // Close resources on error
        }
    }

    /**
     * Waits for incoming data to process based on the predetermined sending method.
     */
    private void waitForSending() {
        new Thread(() -> {
            while (true) {
                try {
                    String incomingMethod = bufRead.readLine();
                    if ("##SENDINGTCPFILE".equals(incomingMethod)) {
                        TCPReceiveFile();
                    } else if ("##SENDINGRBUDPFILE".equals(incomingMethod)) {
                        // Placeholder for RBUDP receiving logic.
                        System.out.println("Starting RBUDP Receive");
                        RBUDPReceiveFile();
                    } else {
                        bufWrite.write("##NOTHINGNOTRECEIVED");
                        bufWrite.newLine();
                        bufWrite.flush();
                    }
                } catch (IOException e) {
                    guiReceiver.showErrorDialog("Sender disconnected");
                    // closeResources();
                    break; // Exit the loop if there's an error.
                }
            }
        }).start();
    }

    /**
     * Handles receiving a file over TCP, saving it to a temporary directory, and
     * updating the GUI.
     */
    private void TCPReceiveFile() {
        try {
            // Read the file name and expected size.
            String fileName = bufRead.readLine();
            long fileSize = Long.parseLong(bufRead.readLine());

            // Ensure the temporary directory exists.
            File tempDir = new File(TEMP_DIR);
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            // Create a new file output stream for the incoming file.
            File file = new File(tempDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalRead = 0;

                // Read the file data from the socket and write it to the file.
                while (totalRead < fileSize && (bytesRead = socket.getInputStream().read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                    final double progress = totalRead / (double) fileSize;
                    guiReceiver.updateProgressBar(progress); // Update the GUI's progress bar.
                }
                System.out.println("File " + fileName + " received and stored temporarily.");
                guiReceiver.updateReceivedList(file.getAbsolutePath()); // Add the received file to the GUI's list.
            }
        } catch (IOException e) {
            closeResources();
            e.printStackTrace();
        }
    }

    // global variables for RBUDP receiving
    private int packetSize = 8192;
    private volatile int size, sequenceNumber, finalSequenceNumber, numberOfPackets;
    private volatile Boolean finalPacket;
    private volatile ArrayList<Integer> sequencesReceived;
    private volatile HashMap<Integer, byte[]> partsOfFile;
    private volatile Boolean finishedReceiving;
    private volatile Boolean UDPStillReciving;

    /**
     * Receives a file using Reliable Broadcast UDP (RBUDP) protocol.
     */
    private void RBUDPReceiveFile() {
        try {

            // Read the file name from the input buffer
            String fileName = bufRead.readLine();

            // Read the size of the file from the input buffer
            size = Integer.parseInt(bufRead.readLine());

            // Calculates the number of packets needed to receive the file
            numberOfPackets = (int) Math.ceil(
                    (double) size / packetSize);

            // Print a message indicating the file being received
            System.out.println("RBUDP: Receiving file: " + fileName + "\n");

            // Initialize lists and variables for managing received data
            sequencesReceived = new ArrayList<>();
            partsOfFile = new HashMap<>();
            finalPacket = false;
            finishedReceiving = false;
            UDPStillReciving = true;

            sequenceListsReceived(); // Receive lists from the sender
            receivePackets(); // Receive the file packets
            writeFileTemp(fileName); // Write received data to a temporary file

        } catch (Exception ex) {
            // Display an error message if the sender disconnects unexpectedly
            closeResources();
            guiReceiver.showErrorDialog("Sender disconnected");
        }
    }

    /**
     * Receives packets and constructs the file from received data.
     * Updates the progress bar as packets are received.
     */
    private void receivePackets() {

        // Initialize packet counter to track progress
        int packetCounter = 0;

        // Make progress bar visible and set initial progress
        guiReceiver.barProgress.setVisible(true);
        guiReceiver.barProgress.setProgress(0);

        // Continuously receive packets until finished
        while (true) {

            try {

                // Create a byte array to hold incoming packet data
                byte[] message = new byte[packetSize + 5];
                byte[] filePartBytes;

                // Check if finished receiving all packets
                if (finishedReceiving) {
                    System.out.println("Done receiving");
                    break;
                }

                // Create a DatagramPacket to receive incoming data
                DatagramPacket datagramPacket = new DatagramPacket(message,
                        message.length);

                try {
                    // Receive the packet
                    datagramSocket.receive(datagramPacket);
                    packetCounter++;
                } catch (Exception e) {
                    // Break loop if an exception occurs
                    closeResources();
                    System.out.println("Sender Disconnected");
                    break;
                }

                // Extract data from the received packet
                message = datagramPacket.getData();

                // Extract sequence number from the received packet
                sequenceNumber = ((message[0] & 0xff) << 16) +
                        ((message[1] & 0xff) << 8) + (message[2] & 0xff);

                // Extract final packet size from the received packet
                int finalPacketSize = ((message[3] & 0xff) << 8) + (message[4] & 0xff);

                finalPacket = (finalPacketSize != 0);

                System.out.println("Received: " + sequenceNumber);

                // Allocate byte array for the file part based on final packet flag
                if (finalPacket) {
                    filePartBytes = new byte[finalPacketSize];
                    System.arraycopy(message, 5, filePartBytes,
                            0, finalPacketSize);
                    finalSequenceNumber = sequenceNumber;
                } else {
                    filePartBytes = new byte[packetSize];
                    System.arraycopy(message, 5, filePartBytes,
                            0, packetSize);
                }

                // Store the received file part in the map
                partsOfFile.put(sequenceNumber, filePartBytes);

                // Add the sequence number to the list of received sequences
                sequencesReceived.add(sequenceNumber);

                // Reset final packet flag
                finalPacket = false;

                // Calculate progress and update the progress bar
                final double progress = packetCounter / (double) numberOfPackets;
                guiReceiver.updateProgressBar(progress); // Update the GUI's progress bar.

            } catch (Exception e) {
                System.out.println("Exception happened");
                closeResources();
                guiReceiver.showErrorDialog("Sender disconnected");
                ;

            }
        }

        // Update status flag and print message
        UDPStillReciving = false;
        System.out.println("finished receiving file");
    }

    /**
     * Writes received file parts to a temporary file.
     *
     * @param fileName The name of the file to be written.
     */
    private void writeFileTemp(String fileName) {
        try {

            // Create a directory for temporary files if it doesn't exist
            File tempDir = new File(TEMP_DIR);
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            // Create a File object for the received file
            File fileReceived = new File(TEMP_DIR + fileName);
            FileOutputStream fileOutputStream = new FileOutputStream(fileReceived);

            // Write each received file part to the temporary file
            for (int i = 1; i <= finalSequenceNumber; i++) {
                // Write the file part data to the output stream
                fileOutputStream.write(partsOfFile.get(i));
                // Print confirmation that the file part has been written
                System.out.println("wrote: " + i);

            }
            // Print a message indicating that writing is finished
            System.out.println("Finished writing");
            // Update the GUI with the path of the received file
            guiReceiver.updateReceivedList(fileReceived.getAbsolutePath());

            // Close the FileOutputStream
            fileOutputStream.close();
        } catch (Exception e) {
            closeResources();
            guiReceiver.showErrorDialog("Error Writing File");
        }
    }

    /**
     * Handles receiving and processing sequence lists from the sender.
     * Checks which sequences have not been received and sends the list back to the
     * sender.
     */
    private void sequenceListsReceived() {
        System.out.println("List: Receive sequence list started");

        // Start a new thread to handle receiving and processing sequence lists
        new Thread(new Runnable() {

            @Override
            public void run() {

                // Loop to continuously receive and process sequence lists
                while (true) {
                    // Initialize variables
                    String sequencesNotReceived = "";
                    String[] sequencesToCheck;

                    // Print a message indicating waiting for list
                    System.out.println("List: Waiting for list");
                    try {
                        // Read a sequence list from the input buffer
                        String message = bufRead.readLine();
                        // Print the received sequence list
                        System.out.println("List: Received list: " + message + "\n");
                        // Check if the received message indicates finishing sending
                        if (message == null || message.equals("##FINISHEDSENDING")) {
                            // Print a message indicating finishing receiving and exit the loop
                            System.out.println("List: Done receiving, exiting list receive");
                            // Set finishedReceiving flag to true
                            finishedReceiving = true;
                            // Send a confirmation message to the sender
                            while (UDPStillReciving) {
                                bufWrite.write("##SENDMORE");
                                bufWrite.newLine();
                                bufWrite.flush();
                                Thread.sleep(50);
                            }
                            bufWrite.write("##DONE");
                            bufWrite.newLine();
                            bufWrite.flush();
                            break;
                        }
                        // Remove leading period if present in the received message
                        if (message.startsWith(".")) {
                            message = message.substring(1);
                        }
                        // Split the message into an array of sequences to check
                        sequencesToCheck = message.split("\\.");
                    } catch (Exception ex) {
                        // Print a message indicating sender disconnected
                        System.out.println("List: Sender disconnected");
                        closeResources();
                        guiReceiver.showErrorDialog("Sender disconnected");
                        break;
                    }

                    // Iterate through sequences to check if they have been received
                    for (int i = 0; i < sequencesToCheck.length; i++) {
                        if (!sequencesReceived.contains(Integer.parseInt(sequencesToCheck[i]))) {
                            // If sequence not received, add it to the list of sequences not received
                            System.out.println("List: Did not receive: " + sequencesToCheck[i]);
                            sequencesNotReceived = sequencesNotReceived + "." + sequencesToCheck[i];
                        }
                    }

                    // If no sequences were not received, set a flag to indicate that
                    if (sequencesNotReceived.equals("")) {
                        sequencesNotReceived = "##NOTHINGNOTRECEIVED";
                    }

                    try {
                        // Print a message indicating sending back the list of not received sequences
                        System.out.println("List: Sending list back: " + sequencesNotReceived);
                        // Write the list of not received sequences to the output buffer back to sender
                        bufWrite.write(sequencesNotReceived);
                        bufWrite.newLine();
                        bufWrite.flush();
                    } catch (Exception e) {
                        // Print a message indicating sender is offline
                        System.out.println("List: Sender disconnected");
                        closeResources();
                        guiReceiver.showErrorDialog("Sender disconnected");

                    }
                }

            }
        }).start();
    }

    /**
     * Closes resources related to the receiver.
     */
    public void closeResources() {
        try {
            if (bufWrite != null) {
                bufWrite.close();
            }
            if (bufRead != null) {
                bufRead.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (datagramSocket != null && !datagramSocket.isClosed()) {
                datagramSocket.close();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
