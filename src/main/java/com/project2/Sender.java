package com.project2;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Represents the sender side in a file transfer system, capable of sending
 * files over TCP or UDP.
 */
public class Sender {

    private GUI_Sender guiSender; // The GUI layer associated with this sender.
    private Socket socket; // TCP socket for communication.
    private int port; // Port number for the TCP connection.
    private String address; // IP address for the TCP connection.
    private volatile DatagramSocket datagramSocket; // Datagram socket for UDP communication.
    private volatile int UDPPort; // Port number for the UDP communication.
    private volatile BufferedWriter bufWrite; // Writer for sending data over TCP.
    private volatile BufferedReader bufRead; // Reader for receiving data over TCP.

    /**
     * Constructs a Sender object associated with a GUI_Sender instance.
     *
     * @param guiSender The associated GUI_Sender instance.
     */
    public Sender(GUI_Sender guiSender) {
        this.guiSender = guiSender;
    }

    /**
     * Attempts to establish a TCP connection to the receiver.
     *
     * @param address The IP address of the receiver.
     * @param port    The port number on which the receiver is listening.
     * @return true if the connection was successfully established, false otherwise.
     */
    public Boolean connectToReceiver(String address, int port) {
        try {
            this.port = port;
            this.address = address;
            socket = new Socket(this.address, this.port);
            bufWrite = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            bufRead = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            return true;
        } catch (Exception e) {
            closeResources();
            return false;
        }
    }

    /**
     * Sets up UDP communication by reading the UDP port sent by the receiver.
     */
    public void setupUDP() {
        try {
            String sUDPPort = bufRead.readLine(); // Read the UDP port from the receiver.
            System.out.println("Received: " + sUDPPort);
            UDPPort = Integer.parseInt(sUDPPort); // Parse the received UDP port.
            datagramSocket = new DatagramSocket(); // Initialize the datagram socket for UDP.
            System.out.println("UDP has been setup");
        } catch (Exception e) {
            System.out.println("Printing ERROR");
            closeResources();
            e.printStackTrace();
        }
    }

    /**
     * Sends a file over TCP to the connected receiver.
     *
     * @param file The file to be sent.
     */
    public void sendTCP(File file) {
        try {
            bufWrite.write("##SENDINGTCPFILE");
            bufWrite.newLine();
            bufWrite.flush();
            sendTCPMethod(file); // Perform the actual file sending over TCP.
            System.out.println("Started TCP send");
        } catch (IOException e) {
            closeResources();
            e.printStackTrace();
        }
    }

    /**
     * Handles the actual file sending over TCP.
     *
     * @param file The file to be sent.
     */
    private void sendTCPMethod(File file) {
        if (file == null) {
            System.err.println("File is null, cannot send over TCP.");
            return;
        }

        if (!file.exists()) {
            System.err.println("File does not exist: " + file.getPath());
            return;
        }

        // Send the file content over TCP.
        try (FileInputStream fis = new FileInputStream(file)) {
            bufWrite.write(file.getName());
            bufWrite.newLine();
            bufWrite.write(Long.toString(file.length()));
            bufWrite.newLine();
            bufWrite.flush();

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                socket.getOutputStream().write(buffer, 0, bytesRead);
            }
            socket.getOutputStream().flush();
            System.out.println("File " + file.getName() + " sent successfully.");
        } catch (IOException e) {
            closeResources();
            e.printStackTrace();
        }
    }

    /**
     * Placeholder for sending a file using RBUDP protocol.
     *
     * @param file The file to be sent using RBUDP.
     */
    public void sendRBUDP(File file) {
        try {
            bufWrite.write("##SENDINGRBUDPFILE");
            bufWrite.newLine();
            bufWrite.flush();
            sendRBUDPMethod(file); // Placeholder method for actual RBUDP file sending.
            System.out.println("Started RBUDP send");
        } catch (IOException e) {
            closeResources();
            e.printStackTrace();
        }
    }

    // Global variables for RBUDP
    private InetAddress inetAddress;
    private static byte[] bytesOfFile;
    private volatile Boolean EOF;
    private volatile String sequencesSent;
    private int listSize = 1000;
    private int packetSize = 8192;

    /**
     * Sends a file using the Reliable Broadcast UDP (RBUDP) protocol.
     *
     * @param file The file to be sent.
     */
    private void sendRBUDPMethod(File file) {

        try {
            // Get the InetAddress for the destination address
            inetAddress = InetAddress.getByName(address);

            // Read the contents of the file into a byte array
            bytesOfFile = new byte[(int) file.length()];
            FileInputStream fileInputStream = new FileInputStream(file);
            fileInputStream.read(bytesOfFile);
            fileInputStream.close();

            // Write the file name to the output buffer
            bufWrite.write(file.getName());
            bufWrite.newLine();
            bufWrite.flush();

            // Write the file size to the output buffer
            bufWrite.write(Integer.toString(bytesOfFile.length));
            bufWrite.newLine();
            bufWrite.flush();

            // Print a message indicating the file name and size sent
            System.out.println("RBUDP: Sent filename: " + file.getName()
                    + "\nRBUDP: Sent file size:" + bytesOfFile.length);

            // Initialize sequence number and sequences sent
            int sequenceNr = 0;
            sequencesSent = "";
            EOF = false;

            // Loop through the file data and send packets
            for (int i = 0; i < bytesOfFile.length; i += packetSize) {

                // Increment sequence number
                sequenceNr += 1;
                // Send packet with current sequence number
                sendPacket(i, sequenceNr);
                // Print a message indicating the sequence number sent
                System.out.println("Sent: " +
                        sequenceNr);
                // Add sequence number to list of sequences sent
                sequencesSent = sequencesSent + "." + sequenceNr;

                // Check if it's time to send a sequence list or if it's end of file
                if (sequenceNr % listSize == 0 || EOF) {
                    // Send the sequence list
                    sendList();
                    sequencesSent = ""; // Clear the list of sequences sent
                }

            }

            // Print a message indicating file sending completion
            System.out.println("FIle sending complete. File size: " + bytesOfFile.length);

            // Signal the end of file sending to the receiver
            bufWrite.write("##FINISHEDSENDING");
            bufWrite.newLine();
            bufWrite.flush();

            // Continue sending packets if receiver requests more
            while (true) {
                String finalCheck = bufRead.readLine();
                if (finalCheck.equals("##SENDMORE")) {
                    sendPacket(0, 1);
                } else {
                    break;
                }
            }

        } catch (Exception ex) {
            closeResources();
            guiSender.showErrorDialog("Receiver Disconnected");
        }

    }

    /**
     * Sends a packet containing a portion of the file data.
     *
     * @param index      The starting index of the portion of the file data to send.
     * @param sequenceNr The sequence number of the packet.
     */
    private void sendPacket(int index, int sequenceNr) {
        // Create a byte array to hold the packet data
        byte[] message = new byte[5 + packetSize];

        // Set the sequence number in the packet header
        message[0] = (byte) (sequenceNr >> 16);
        message[1] = (byte) (sequenceNr >> 8);
        message[2] = (byte) sequenceNr;

        // Set the final packet size in the packet header
        message[3] = (byte) 0 >> 8;
        message[4] = (byte) 0;

        // Check if it's the last packet
        if ((index + packetSize) >= bytesOfFile.length) {
            EOF = true;

            // Set the final packet size in the packet header
            message[3] = (byte) ((bytesOfFile.length - index) >> 8);
            message[4] = (byte) (bytesOfFile.length - index);

            // Copy file data to the message array
            System.arraycopy(bytesOfFile, index, message,
                    5, bytesOfFile.length - index);
        } else {
            // Copy file data to the message array
            System.arraycopy(bytesOfFile, index, message,
                    5, packetSize);
        }

        // Create a DatagramPacket with the message and send it
        DatagramPacket sendPacket = new DatagramPacket(
                message, message.length, inetAddress,
                UDPPort);
        try {
            datagramSocket.send(sendPacket);
        } catch (IOException e) {
            closeResources();
            // Print a message if the packet cannot be sent
            System.out.println("Packet unable to be sent");
        }

    }

    /**
     * Sends a list of sequence numbers to the receiver and handles resending
     * missing packets.
     */
    private void sendList() {
        // Print a message indicating sending sequence number list
        System.out.println("Sending sequence number list");

        // Continuously send and handle missing packets until all packets are received
        while (true) {
            try {
                // Send the list of sequence numbers that have been sent
                System.out.println("Sending list :" + sequencesSent);
                bufWrite.write(sequencesSent);
                bufWrite.newLine();
                bufWrite.flush();
                String sequencesNotSent = bufRead.readLine();

                System.out.println("received list: " + sequencesNotSent);

                // Reset the list of sequences sent
                sequencesSent = "";

                // Check if there are missing packets to be resent
                if (!sequencesNotSent.equals("##NOTHINGNOTRECEIVED")) {
                    // Remove leading period if present
                    if (sequencesNotSent.startsWith("."))
                        sequencesNotSent = sequencesNotSent.substring(1);
                    // Split the list into individual sequence numbers
                    String[] parts = sequencesNotSent.split("\\.");
                    // Resend missing packets
                    for (int i = 0; i < parts.length; i++) {
                        int sequenceNumber_ = Integer.parseInt(parts[i]);
                        sendPacket(sequenceNumber_ * packetSize - packetSize, sequenceNumber_);
                        sequencesSent = sequencesSent + "." + parts[i];
                        System.out.println("Resending: " + parts[i]);
                    }
                } else {
                    // Print a message indicating all packets have been resent
                    System.out.println("Resending completed");
                    break;
                }

            } catch (Exception ex) {
                // Print a stack trace if an exception occurs and exit the program
                closeResources();
                ex.printStackTrace();
                System.exit(0);
            }
        }
    }

    /**
     * Closes sender resources.
     */
    public void closeResources() {
        try {
            if (bufWrite != null) {
                bufWrite.close();
            }
            if (bufRead != null) {
                bufRead.close();
            }
            if (socket != null) {
                socket.close();
            }
            if (datagramSocket != null) {
                datagramSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
