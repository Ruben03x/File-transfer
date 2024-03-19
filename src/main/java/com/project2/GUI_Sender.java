package com.project2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * The GUI that users employ to send files.
 */
public class GUI_Sender extends Application {

    @FXML
    private Button btnConnect; // the JavaFX "button" that users click to connect

    @FXML
    private Button btnUpload; // the JavaFX "button" that users click to upload a file

    @FXML
    private Button btnSend; // the JavaFX "button" that users click to send a file

    @FXML
    private volatile ListView<String> listFiles; // the list of files

    @FXML
    private ListView<String> listLog; // the sent files: displayed as a list

    @FXML
    private volatile RadioButton radioRBUDP; // the "button" that users select to choose RBUDP as the file sharing
                                             // construct

    @FXML
    private volatile RadioButton radioTCP; // the "button" that users select to choose TCP as the file sharing construct

    @FXML
    private TextField textAddress; // the address line used when connecting

    @FXML
    private TextField textPort; // the port number used when connecting

    private Sender sender = new Sender(this); // the current sender
    private File selectedFile; // a file selected by the user in the GUI
    private volatile ArrayList<File> uploadedFiles = new ArrayList<>(); // a running list of files uploaded by the
                                                                        // sender

    /**
     * Initializes a connection to the receiver.
     * 
     * Uses the reciever's address and port number, obtained from text fields in the
     * GUI, to establish said connection.
     * Initiates setup of TCP and UDP connections, and handles errors such as an
     * unavailable receiver or invalid port.
     * 
     * @param event The event triggering the connection (interaction with the
     *              btnConnect button)
     */
    @FXML
    void connectToReceiver(ActionEvent event) {
        try {
            String address = textAddress.getText();
            int port = Integer.parseInt(textPort.getText());
            Boolean TCPConnected = sender.connectToReceiver(address, port); // sets up TCP

            if (TCPConnected) {
                btnConnect.setDisable(true);
                textAddress.setDisable(true);
                textPort.setDisable(true); // disable the connect button and address and port text fields to ensure
                                           // there are no further modifications
                showDialog("Connected to Receiver");
                sender.setupUDP(); // sets up UDP
            } else {
                showErrorDialog("Receiver not available");
            }

        } catch (Exception e) {
            showErrorDialog("Invalid port");
        }

    }

    @FXML
    /**
     * Uploads a file.
     * 
     * Adds the filename to the list of uploaded files and updates the listview to
     * display the list of uploaded files.
     * 
     * @param event A mouse click on the btnUpload button.
     */
    void uploadFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        selectedFile = fileChooser.showOpenDialog(btnUpload.getScene().getWindow());

        if (selectedFile != null) {
            uploadedFiles.add(selectedFile); // add selected file to the list of uploaded files
            listFiles.getItems().clear();
            // Use the file name instead of the entire file object
            List<String> fileNames = uploadedFiles.stream().map(File::getName).collect(Collectors.toList());
            listFiles.getItems().setAll(fileNames); // updates list of uploaded files
            listFiles.setVisible(true);
        } else {
            showErrorDialog("No file was selected.");
        }
    }

    @FXML
    /**
     * Initiates sending a file selected by the user.
     * 
     * Retrieves the index of the selected file, uses said index to get the file
     * from the list fo uploaded files, and displays the selected file name along
     * with the transmission method used. Then employs the sender to send the file.
     * 
     * @param event a button press, usually on the btnSend button.
     */
    void sendFIle(ActionEvent event) {
        int selectedIndex = listFiles.getSelectionModel().getSelectedIndex();

        if (selectedIndex == -1) {
            showErrorDialog("No file selected to send.");
            return;
        }

        File selectedFile = uploadedFiles.get(selectedIndex); // retrieve selected file name

        if (radioRBUDP.isSelected()) {
            sender.sendRBUDP(selectedFile); // init sending with RBUDP
            // Log the sending action
            Platform.runLater(() -> {
                listLog.getItems().add("Sent file (RBUDP): " + selectedFile.getName());
            });
        } else if (radioTCP.isSelected()) {
            sender.sendTCP(selectedFile); // init sending with TCP
            // Log the sending action
            Platform.runLater(() -> {
                listLog.getItems().add("Sent file (TCP): " + selectedFile.getName());
            });
        } else {
            showErrorDialog("No sending method was selected");
        }
    }

    @FXML
    /**
     * Deselects the RBUDP RadioButton.
     * 
     * @param event mouse click, usually on the radioTCP RadioButton
     */
    void deselectRBUDP(ActionEvent event) {
        radioRBUDP.setSelected(false);
    }

    @FXML
    /**
     * Deselects the TCP RadioButton.
     * 
     * @param event mouse click, usually on the radioRBUDP RadioButton
     */
    void deselectTCP(ActionEvent event) {
        radioTCP.setSelected(false);
    }

    @Override
    /**
     * Initializes the GUI using JavaFX tools.
     * 
     * @param primaryStage The "stage" JavaFX object.
     */
    public void start(Stage primaryStage) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/project2/GUI_Sender.fxml"));
            primaryStage.setScene(new Scene(root));
            primaryStage.setTitle("Sender Screen");
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Main method: launches the GUI.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Prints an error dialog to the GUI.
     * 
     * @param message The error message to print.
     */
    public void showErrorDialog(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Outputs information to the GUI.
     * 
     * @param message The information to display.
     */
    public void showDialog(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}