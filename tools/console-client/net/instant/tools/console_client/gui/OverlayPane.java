package net.instant.tools.console_client.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.KeyboardFocusManager;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.OverlayLayout;
import javax.swing.SwingUtilities;

public class OverlayPane extends JLayeredPane {

    public interface ChildHolder {

        Component getChild();

        void setChild(Component newChild);

    }

    public static class Backdrop extends JPanel implements ChildHolder,
            MouseListener {

        public static final Color BACKGROUND = new Color(0x80000000, true);

        private Component child;

        public Backdrop() {
            initBackdrop();
        }

        protected void initBackdrop() {
            setBackground(BACKGROUND);
            setOpaque(false);
            addMouseListener(this);
            setLayout(new GridBagLayout());
        }

        public Component getChild() {
            return child;
        }
        public void setChild(Component newChild) {
            if (newChild == child) return;
            Component oldChild = child;
            child = newChild;
            replaceChild(oldChild, newChild);
            // A mere invalidate() does not always suffice; in GUIClient,
            // changing the statusLabel's text before swapping the dialog in
            // would result in the label ellipsizing its text rather than
            // assuming a correct new width.
            revalidate();
            repaint();
        }

        protected void replaceChild(Component oldChild, Component newChild) {
            rotateChildren(this, oldChild, newChild);
        }

        public void paintComponent(Graphics g) {
            // If isOpaque() is true, the painting system does not render
            // anything behind us; if isOpaque() is false, JPanel does not
            // render its background. The latter being more easy to correct,
            // we do it here.
            if (! isOpaque()) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            super.paintComponent(g);
        }

        public void mouseEntered(MouseEvent evt) {}
        public void mousePressed(MouseEvent evt) {}
        public void mouseReleased(MouseEvent evt) {}
        public void mouseClicked(MouseEvent evt) {}
        public void mouseExited(MouseEvent evt) {}

        protected static void rotateChildren(Container holder,
                Component oldChild, Component newChild) {
            if (oldChild != null) holder.remove(oldChild);
            if (newChild != null) holder.add(newChild);
        }

    }

    // HACK: Swing's default LAF uses the LayoutFocusTraversalPolicy; we
    //       assume that the actually used LAF will do so, too. An arguably
    //       better approach would be to locate the FTP responsible for the
    //       component and proxying to it, but this is easier.
    protected class OverlayFocusTraversalPolicy
            extends LayoutFocusTraversalPolicy {

        protected boolean accept(Component comp) {
            if (! super.accept(comp))
                return false;
            if (content != null && backdrop.isVisible() &&
                    SwingUtilities.isDescendingFrom(comp, content))
                return false;
            return true;
        }

    }

    private Component content;
    private Component backdrop;

    public OverlayPane() {
        initOverlayPane();
    }

    protected void initOverlayPane() {
        setFocusTraversalPolicyProvider(true);
        setFocusTraversalPolicy(new OverlayFocusTraversalPolicy());
        setLayout(new OverlayLayout(this));
        backdrop = new Backdrop();
        backdrop.setVisible(false);
        add(backdrop, MODAL_LAYER);
    }

    public Component getContent() {
        return content;
    }
    public void setContent(Component newContent) {
        if (newContent == content) return;
        if (content != null) remove(content);
        content = newContent;
        if (content != null) add(content, DEFAULT_LAYER);
    }

    public Component getBackdrop() {
        return backdrop;
    }
    public void setBackdrop(Component newBackdrop) {
        if (newBackdrop == backdrop) return;
        remove(backdrop);
        backdrop = newBackdrop;
        add(backdrop, MODAL_LAYER);
    }

    public Component getOverlay() {
        if (backdrop instanceof ChildHolder) {
            return ((ChildHolder) backdrop).getChild();
        } else {
            return backdrop;
        }
    }
    public void setOverlay(Component newOverlay) {
        if (backdrop instanceof ChildHolder) {
            ((ChildHolder) backdrop).setChild(newOverlay);
        } else {
            Backdrop back = new Backdrop();
            back.setVisible(false);
            back.setChild(newOverlay);
            setBackdrop(back);
        }
    }

    public boolean isOverlayVisible() {
        return backdrop.isVisible();
    }
    public void setOverlayVisible(boolean visible) {
        backdrop.setVisible(visible);
        Component cnt = getContent(), ovl = getOverlay();
        if (cnt == null || ovl == null) return;
        KeyboardFocusManager mgr =
            KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Component focusOwner = mgr.getFocusOwner();
        if (focusOwner == null) return;
        Component forbiddenParent, replacementParent;
        if (visible) {
            forbiddenParent = cnt;
            replacementParent = ovl;
        } else {
            forbiddenParent = ovl;
            replacementParent = cnt;
        }
        if (SwingUtilities.isDescendingFrom(focusOwner, forbiddenParent)) {
            Component newOwner;
            if (replacementParent instanceof Container) {
                newOwner = getFocusTraversalPolicy()
                    .getDefaultComponent((Container) replacementParent);
            } else {
                newOwner = replacementParent;
            }
            if (newOwner != null) {
                newOwner.requestFocusInWindow();
            }
        }
    }
    public void toggleOverlayVisible() {
        setOverlayVisible(! isOverlayVisible());
    }

}
