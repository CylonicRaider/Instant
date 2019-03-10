package net.instant.tools.console_client.gui;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

public class EnumSelector<E extends Enum<E>> extends JPanel
        implements ItemListener {

    public interface Model<E> {

        Class<E> getEnumClass();

        String getDescription(E query);

        E getSelected();

        void setSelected(E value);

    }

    private final Model<E> model;
    private final ButtonGroup group;

    public EnumSelector(Model<E> model) {
        this.model = model;
        this.group = new ButtonGroup();
        createUI();
    }

    protected void createUI() {
        setBorder(BorderFactory.createTitledBorder(
            model.getDescription(null)));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        E selected = model.getSelected();
        for (E entry : model.getEnumClass().getEnumConstants()) {
            JRadioButton rb = new JRadioButton(model.getDescription(entry),
                (entry == selected));
            rb.setActionCommand(entry.name());
            rb.addItemListener(this);
            group.add(rb);
            add(rb);
        }
    }

    public void itemStateChanged(ItemEvent evt) {
        if (evt.getStateChange() != ItemEvent.SELECTED ||
            ! (evt.getItem() instanceof JRadioButton)) return;
        JRadioButton source = (JRadioButton) evt.getItem();
        if (source.getParent() != this) return;
        E newSelected = Enum.valueOf(model.getEnumClass(),
                                     source.getActionCommand());
        model.setSelected(newSelected);
    }

    public static <E extends Enum<E>> EnumSelector<E>
            createFor(Model<E> model) {
        return new EnumSelector<E>(model);
    }

}
