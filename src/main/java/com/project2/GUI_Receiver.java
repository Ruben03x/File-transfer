package com.project2;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

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
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.FileChooser;

/**
 * The GUI that users employ to receive files.
 */
public class GUI_Receiver extends Application {

    @FXML
    public ProgressBar barProgress; // The progress bar that shows the portion of the file that has been received.

    @FXML
    private Button btnSave; // The interactive "save" button.

    @FXML
    private Button btnSetPort; // The button used to set the port when connecting.

    @FXML
    private ListView<String> listReceived; // Strings representing names of files received.

    @FXML
    private TextField textPort; // Text representing the port number.

    private Receiver receiver = new Receiver(this); // The current receiver.
    private Boolean TCPConnected = false; // Boolean that indicates whether the TCP is connected.

    private Map<String, String> fileNameToPathMap = new HashMap<>(); // Holds information about file paths.

    /**
     * Sets the port for the TCP connection.
     * 
     * This method is responsible for configuring the port for the TCP connection
     * when the user sets the port.
     * It attempts to parse the port number from the text input provided by the
     * user, and then invokes
     * the startTCPConnection method of the Receiver object to establish a TCP
     * connection
     * using the specified port.
     * If the TCP connection is successfully established, the method displays a
     * notification dialog indicating
     * that the TCP connection is running on the specified port.
     * 
     * If the port is already in use or if an invalid port number is provided, an
     * error is displayed.
     * Any exceptions that occur during the port parsing or TCP connection setup
     * process are caught
     * and handled by displaying an error with an appropriate message.
     *
     * @param event The ActionEvent object representing the event that triggered the
     *              method call.
     */
    @FXML
    void setPort(ActionEvent event) {
        try {
            int port = Integer.parseInt(textPort.getText());
            TCPConnected = receiver.startTCPConnection(port);

            if (TCPConnected) {
                textPort.setDisable(true);
                btnSetPort.setDisable(true); // disables interactive port buttons upon successful connection.
                showDialog("TCP connection running on port: " + port);
            } else {
                showErrorDialog("Port " + port + " already in use.");
            }
        } catch (Exception e) {
            showErrorDialog("Invalid Port: " + textPort.getText());
        }
    }

    /**
     * Saves the selected file to a new location.
     * 
     * This method is invoked when the user initiates saving a file.
     * It retrieves the name of the selected file, obtains the path, and prompts the
     * user to select a location and save the file.If the selected file exists and
     * the save operation is successful, a confirmation dialog is displayed
     * indicating the location where the file was saved.
     * 
     * @param event The ActionEvent representing the event that
     *              triggered the method call.
     */
    @FXML
    void saveFile(ActionEvent event) {
        String selectedFileName = listReceived.getSelectionModel().getSelectedItem();
        if (selectedFileName != null && !selectedFileName.isEmpty()) {
            // Retrieve the full path from the map using the selected file name
            String originalFilePath = fileNameToPathMap.get(selectedFileName);
            if (originalFilePath != null) {
                File originalFile = new File(originalFilePath);

                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save File");
                fileChooser.setInitialFileName(originalFile.getName());

                File savedFile = fileChooser.showSaveDialog(null);

                if (savedFile != null) {
                    try {
                        if (originalFile.exists()) {
                            Files.copy(originalFile.toPath(), savedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            showDialog("File saved successfully to " + savedFile.getAbsolutePath());
                        } else {
                            showErrorDialog("Original file does not exist.");
                        }
                    } catch (IOException e) {
                        showErrorDialog("Failed to save the file: " + e.getMessage());
                    }
                }
            } else {
                showErrorDialog("File path not found for the selected file.");
            }
        } else {
            showErrorDialog("No file selected.");
        }
    }

    /**
     * Initializes the GUI using JavaFX tools.
     * 
     * @param primaryStage The "stage" JavaFX object.
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/project2/GUI_Receive.fxml"));
            primaryStage.setScene(new Scene(root));
            primaryStage.setTitle("Receive Screen");
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * The "main" method that launches the GUI.
     * 
     * @param args Command line arguments.
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

    /**
     * Updates the GUI's progress bar.
     * 
     * @param progress The double value representing the portion of total packets
     *                 transferred.
     */
    public void updateProgressBar(double progress) {
        Platform.runLater(() -> barProgress.setProgress(progress));
    }

    /**
     * Updates the list of received files.
     * 
     * @param receivedFilePath The path of the received file.
     */
    public void updateReceivedList(String receivedFilePath) {
        Platform.runLater(() -> {
            File file = new File(receivedFilePath);
            listReceived.getItems().add(file.getName());
            fileNameToPathMap.put(file.getName(), receivedFilePath); // Map file name to its full path
        });
    }
}