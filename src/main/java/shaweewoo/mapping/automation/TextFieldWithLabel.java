package shaweewoo.mapping.automation;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TextFieldWithLabel {

    private Label m_Label;
    private TextField m_TextField;

    private List<Node> m_Children;

    public TextFieldWithLabel(String labelText, String defaultInputText, InputButton... buttons) {
        m_Label = new Label(labelText);
        m_TextField = new TextField(defaultInputText);
        m_Children = new ArrayList<>();
        Collections.addAll(m_Children, m_Label, m_TextField);
        for (InputButton button : buttons) {
            button.setTextFieldWithLabel(this);
            m_Children.add(button);
        }
    }

    public List<Node> getChildren() {
        return m_Children;
    }

    public Label getLabel() {
        return m_Label;
    }

    public TextField getTextField() {
        return m_TextField;
    }
}
