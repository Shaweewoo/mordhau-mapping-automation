package shaweewoo.mapping.automation;

import javafx.scene.control.Button;
import javafx.scene.image.ImageView;

public abstract class InputButton extends Button {

    protected TextFieldWithLabel m_TextFieldWithLabel;

    public InputButton(String iconName) {
        super();
        var icon = new ImageView(MappingAutomation.getImage(iconName));
        icon.setFitWidth(16);
        icon.setFitHeight(16);
        setGraphic(icon);
    }

    public void setTextFieldWithLabel(TextFieldWithLabel textFieldWithLabel) {
        m_TextFieldWithLabel = textFieldWithLabel;
    }
}
