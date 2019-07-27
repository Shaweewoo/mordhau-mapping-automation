package shaweewoo.mapping.automation;

import javafx.stage.DirectoryChooser;

import java.io.File;

public class BrowseButton extends InputButton {

    public BrowseButton() {
        super("browse.png");
        setOnAction(action -> {
            if (m_TextFieldWithLabel != null) {
                var directoryChooser = new DirectoryChooser();
                var currentFile = new File(m_TextFieldWithLabel.getTextField().getText());
                if (currentFile.exists()) {
                    directoryChooser.setInitialDirectory(currentFile);
                }
                var selectedDirectory = directoryChooser.showDialog(MappingAutomation.singleton().getStage());
                if (selectedDirectory != null) {
                    m_TextFieldWithLabel.getTextField().setText(selectedDirectory.toString());
                }
            }
        });
    }
}
