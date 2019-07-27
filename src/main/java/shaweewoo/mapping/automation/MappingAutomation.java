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
        stage.setTitle("Mordhau Mapping Automation v0.3 - By Shaweewoo");

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
                System.out.println(String.format("Failed to read default config %s", exception.getLocalizedMessage()));
            }
        }

        String
                binaryPath = defaultPaths.getOrDefault("BinaryDirectory", DEFAULT_UE4_4_21_BINARY_PATH),
                mordhauInstallPath = defaultPaths.getOrDefault("MordhauInstallDirectory", DEFAULT_MORDHAU_INSTALL_PATH),
                unrealProjectPath = defaultPaths.getOrDefault("UnrealProjectDirectory", Paths.get(System.getProperty("user.home"), "Documents", "Unreal Projects").toString()),
                customCopyPath = defaultPaths.getOrDefault("UnrealProjectCustomCopyDirectory", null);

        var binaryLocationEntry = new TextFieldWithLabel("Unreal Engine 4.21 Binary Location", binaryPath, new BrowseButton());
        addRow(binaryLocationEntry);
        var mordhauInstallEntry = new TextFieldWithLabel("Mordhau Install Location", mordhauInstallPath, new BrowseButton());
        addRow(mordhauInstallEntry);
        var mapFolderEntry = new TextFieldWithLabel("Map Folder Location", unrealProjectPath, new BrowseButton());
        addRow(mapFolderEntry);
        var customFolderEntry = new TextFieldWithLabel("Custom Copy Folder", customCopyPath, new BrowseButton());
        addRow(customFolderEntry);

        var output = new Label("Console Output Will Appear Here");
        output.setWrapText(true);
        var outputScroll = new ScrollPane(output);
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
            // Run later allows us to interact with JavaFX from our own thread, the library is not concurrent
            Platform.runLater(() -> output.setText(""));
            try {
                var exe = Paths.get(binaryLocationEntry.getTextField().getText(), "UE4Editor.exe");
                var mapPath = Paths.get(mapFolderEntry.getTextField().getText());
                if (Files.exists(exe) && Files.exists(mapPath)) {
                    // We are assuming that the map is put in Contents/<Map Name> two folders down from project
                    var projectPath = mapPath.getParent().getParent();
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
                                final String copiedLine = outputLine;
                                System.out.println(copiedLine);
                                Platform.runLater(() -> output.setText(output.getText() + "\n" + copiedLine));
                            }
                            while ((outputLine = errorOutput.readLine()) != null) {
                                final String copiedLine = outputLine;
                                System.out.println(copiedLine);
                                Platform.runLater(() -> output.setText(output.getText() + "\n" + copiedLine));
                            }
                            try {
                                process.waitFor();
                            } catch (InterruptedException exception) {
                                exception.printStackTrace();
                            }
                            System.out.println("Finished cooking... Supposedly");
                            var cookedPath = Paths.get(projectPath.toString(), "Saved", "Cooked", "WindowsNoEditor", "MordhauMap", "Content", mapName.toString()).toFile();
                            var mordhauMapPath = Paths.get(mordhauInstallPath, "Mordhau", "Content", "Mordhau", "Maps", mapName.toString());
                            boolean madeDirectory = mordhauMapPath.toFile().mkdir();
                            if (madeDirectory) {
                                System.out.println(String.format("Made directory %s", mordhauMapPath));
                            }
                            if (cookedPath.exists()) {
                                File[] mapFiles = cookedPath.listFiles();
                                if (mapFiles != null) {
                                    try {
                                        for (File mapFile : mapFiles) {
                                            var mapFileName = mapFile.toPath().getFileName();
                                            Files.copy(mapFile.toPath(), Paths.get(mordhauMapPath.toString(), mapFileName.toString()), StandardCopyOption.REPLACE_EXISTING);
                                            System.out.println(String.format("Copied %s to %s", mapFile.toPath(), Paths.get(mordhauMapPath.toString(), mapFileName.toString())));
                                        }
                                    } catch (IOException exception) {
                                        // Usually if this happens the user is in-game
                                        System.out.println(String.format("Error copying files over: %s", exception.getLocalizedMessage()));
                                    }
                                }
                            } else {
                                System.out.println("Can't find cooked items!");
                            }
                        }
                    }
                }
            } catch (Exception exception) {
                System.out.println(String.format("Something went wrong: %s", exception.getLocalizedMessage()));
            } finally {
                executionMutex.unlock();
            }
        }).start());

        var buttonGroup = new HBox(cookAndCopy);
        buttonGroup.getStyleClass().add("spacSing");

        var mainContainer = new VBox(m_MainGrid, new Separator(), buttonGroup, outputScroll);
        mainContainer.getStyleClass().addAll("main-container", "spacing", "padding");

        var screenBounds = Screen.getPrimary().getBounds();
        var scene = new Scene(mainContainer, screenBounds.getHeight(), screenBounds.getHeight() * 0.6);

        var stylesheet = (getClass().getClassLoader().getResource("stylesheet.css"));
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());

        stage.setScene(scene);
        stage.show();
    }
}
