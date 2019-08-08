package shaweewoo.mapping.automation;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

public class MappingAutomation extends Application {

    private static final String
            VERSION = "v0.4",
            DEFAULT_MORDHAU_INSTALL_PATH = "C:\\Program Files (x86)\\Steam\\steamapps\\common\\Mordhau",
            DEFAULT_UE4_4_21_BINARY_PATH = "C:\\Program Files\\Epic Games\\UE_4.21\\Engine\\Binaries\\Win64";
    private static int s_RowIndex;
    private GridPane m_MainGrid;
    private Stage m_Stage;

    private static MappingAutomation s_Singleton;

    public static MappingAutomation singleton() {
        return s_Singleton;
    }

    public static void main(String... arguments) {
        launch(arguments);
    }

    public Stage getStage() {
        return m_Stage;
    }

    public static Image getImage(String path) {
        var resourceStream = MappingAutomation.class.getClassLoader().getResourceAsStream(path);
        return resourceStream == null ? null : new Image(resourceStream);
    }

    private void addRow(TextFieldWithLabel entry) {
        // Stupid java with the toArray method... seriously
        m_MainGrid.addRow(s_RowIndex++, entry.getChildren().toArray(new Node[0]));
    }

    @Override
    public void start(Stage stage) {
        s_Singleton = this;
        m_Stage = stage;

        var icon = getImage("icon.png");
        if (icon != null) stage.getIcons().add(icon);
        stage.setTitle(String.format("Mordhau Mapping Automation %s - By Shaweewoo", VERSION));

        m_MainGrid = new GridPane();
        m_MainGrid.getStyleClass().addAll("grid-pane");
        var expandTextFieldConstraint = new ColumnConstraints();
        expandTextFieldConstraint.setHgrow(Priority.ALWAYS);
        m_MainGrid.getColumnConstraints().addAll(new ColumnConstraints(), expandTextFieldConstraint);

        var defaultFile = new File("Defaults.txt");
        var defaultPaths = new HashMap<String, String>();
        if (defaultFile.exists()) {
            System.out.println(String.format("Reading defaults from file: %s", defaultFile.getAbsolutePath()));
            try {
                var lines = Files.readAllLines(defaultFile.toPath());
                for (var line : lines) {
                    var pieces = line.split("=");
                    if (pieces.length == 2) {
                        defaultPaths.put(pieces[0], pieces[1]);
                    }
                }
            } catch (IOException exception) {
                System.out.println(String.format("Failed to read default config %s", exception));
            }
        }

        String
                binaryPath = defaultPaths.getOrDefault("BinaryDirectory", DEFAULT_UE4_4_21_BINARY_PATH),
                mordhauInstallPath = defaultPaths.getOrDefault("MordhauInstallDirectory", DEFAULT_MORDHAU_INSTALL_PATH),
                unrealProjectPath = defaultPaths.getOrDefault("UnrealProjectDirectory", Paths.get(System.getProperty("user.home"), "Documents", "Unreal Projects").toString()),
                customCopyPath = defaultPaths.getOrDefault("UnrealProjectCustomCopyDirectory", "");

        var binaryLocationEntry = new TextFieldWithLabel("Unreal Engine 4.21 Binary Location", binaryPath, new BrowseButton());
        addRow(binaryLocationEntry);
        var mordhauInstallEntry = new TextFieldWithLabel("Mordhau Install Location", mordhauInstallPath, new BrowseButton());
        addRow(mordhauInstallEntry);
        var mapFolderEntry = new TextFieldWithLabel("Map Folder Location", unrealProjectPath, new BrowseButton());
        addRow(mapFolderEntry);
        var customFolderEntry = new TextFieldWithLabel("Custom Copy Folder", customCopyPath);
        addRow(customFolderEntry);

        var output = new Label("Console Output Will Appear Here");
        output.setWrapText(true);
        var outputScroll = new ScrollPane(output);
        var outputText = new StringBuilder();
        // Crude way to make it auto-scroll
        outputScroll.vvalueProperty().bind(output.heightProperty());

        var executionMutex = new ReentrantLock(true);
        // TODO add just cook or just copy buttons, refactor each into methods
        Button cookButton = new Button("Cook"), copyButton = new Button("Copy"), cookAndCopy = new Button("Cook & Copy");
        cookAndCopy.setOnAction(action -> new Thread(() -> {
            // We do not want to run cook and copy commands at the same time
            if (!executionMutex.tryLock()) {
                return;
            }
            outputText.setLength(0);
            Runnable updateText = () -> output.setText(outputText.toString());
            // Run later allows us to interact with JavaFX from our own thread, the library is not concurrent
            Platform.runLater(updateText);
            try {
                var exe = Paths.get(binaryLocationEntry.getTextField().getText(), "UE4Editor.exe");
                var mapPath = Paths.get(mapFolderEntry.getTextField().getText());
                if (Files.exists(exe) && Files.exists(mapPath)) {
                    // We are assuming that the map is put in Contents/<Map Name> two folders down from project
                    var projectPath = mapPath.getParent().getParent();
                    var projectName = projectPath.getFileName().toString();
                    File[] projectFiles = projectPath.toFile().listFiles();
                    if (projectFiles != null) {
                        File unrealProjectFile = null;
                        for (var file : projectFiles) {
                            if (file.toString().endsWith(".uproject")) {
                                System.out.println(file.toPath());
                                unrealProjectFile = file;
                            }
                        }
                        if (unrealProjectFile != null) {
                            var mapName = mapPath.getFileName();
                            // UnVersioned is require to make sure it does not crash Mordhau
                            // Assuming also that the name of the map is the same as the folder it is in
                            var command = String.format(
                                    "\"%s\" \"%s\" -run=cook -targetplatform=WindowsNoEditor -UnVersioned -Map=/Game/%s/%s",
                                    exe, unrealProjectFile, mapName, mapName);
                            System.out.println(String.format("Using command: %s", command));
                            var process = Runtime.getRuntime().exec(command, null, projectPath.toFile());
                            var standardOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                            var errorOutput = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                            String outputLine;
                            while ((outputLine = standardOutput.readLine()) != null) {
                                outputText.append(outputLine);
                                outputText.append("\n");
                                Platform.runLater(updateText);
                            }
                            while ((outputLine = errorOutput.readLine()) != null) {
                                final String copiedLine = outputLine;
                                outputText.append(outputLine);
                                outputText.append("\n");
                                Platform.runLater(updateText);
                            }
                            try {
                                process.waitFor();
                            } catch (InterruptedException exception) {
                                exception.printStackTrace();
                            }
                            System.out.println("Finished cooking... Supposedly");
                            var contentCookedPath = Paths.get(projectPath.toString(), "Saved", "Cooked", "WindowsNoEditor", projectName, "Content");
                            var mapCookedPath = Paths.get(contentCookedPath.toString(), mapName.toString()).toFile();
                            var mordhauContentDirectory = Paths.get(mordhauInstallPath, "Mordhau", "Content").toFile();
                            var customContentDirectoryName = customFolderEntry.getTextField().getText();
                            if (customContentDirectoryName != null && !customContentDirectoryName.isEmpty()) {
                                var customCookedPath = Paths.get(contentCookedPath.toString(), customContentDirectoryName).toFile();
                                var mordhauCustomContentDirectory = Paths.get(mordhauContentDirectory.toString(), customContentDirectoryName).toFile();
                                if (!copyFolder(mordhauCustomContentDirectory, customCookedPath)) {
                                    System.err.println("Something went wrong with cooked custom items.");
                                }
                            }
                            var mordhauMapPath = Paths.get(mordhauContentDirectory.toString(), "Mordhau", "Maps", mapName.toString()).toFile();
                            if (!copyFolder(mordhauMapPath, mapCookedPath)) {
                                System.err.println("Something went wrong with cooked map items.");
                            }
                        }
                    }
                }
            } catch (Exception exception) {
                exception.printStackTrace();
                System.out.println(String.format("Something went wrong: %s", exception));
            } finally {
                executionMutex.unlock();
            }
        }).start());

        var buttonGroup = new HBox(cookAndCopy);
        buttonGroup.getStyleClass().add("spacing");

        var mainContainer = new VBox(m_MainGrid, new Separator(), buttonGroup, outputScroll);
        mainContainer.getStyleClass().addAll("main-container", "spacing", "padding");

        var screenBounds = Screen.getPrimary().getBounds();
        var scene = new Scene(mainContainer, screenBounds.getHeight(), screenBounds.getHeight() * 0.6);

        var stylesheet = (getClass().getClassLoader().getResource("stylesheet.css"));
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());

        stage.setScene(scene);
        stage.show();
    }

    private boolean copyFolder(File toPath, File fromPath) {
        var copyFiles = fromPath.listFiles();
        if (copyFiles == null || !fromPath.exists()) {
            System.err.println("Requested files to copy do not exist!");
            return false;
        }
        if (!toPath.exists()) {
            if (!toPath.mkdirs()) {
                System.err.println(String.format("Failed to make directory %s", toPath));
                return false;
            } else {
                System.out.println(String.format("Made directory %s", toPath));
            }
        }
        try {
            for (File mapFile : copyFiles) {
                var mapFileName = mapFile.toPath().getFileName();
                Files.copy(mapFile.toPath(), Paths.get(toPath.toString(), mapFileName.toString()), StandardCopyOption.REPLACE_EXISTING);
                System.out.println(String.format("Copied %s to %s", mapFile, Paths.get(toPath.toString(), mapFileName.toString())));
            }
            return true;
        } catch (IOException exception) {
            // Usually if this happens the user is in-game
            System.err.println(String.format("Error copying files over: %s", exception));
        }
        return false;
    }
}
