/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.views.common;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Defs;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;
import org.icepdf.core.views.swing.AbstractPageViewComponent;

import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles Paint and mouse/keyboard logic around text selection and search
 * highlighting.  there is on text handler isntance of each pageComponent
 * used to dispaly the document.
 * <p/>
 * The highlight colour by default is #FFF600 but can be set using color or
 * hex values names using the system property "org.icepdf.core.views.page.text.highlightColor"
 * <p/>
 * The highlight colour by default is #FFF600 but can be set using color or
 * hex values names using the system property "org.icepdf.core.views.page.text.selectionColor"
 * <p/>
 *
 * @since 4.0
 */
public class TextSelectionPageHandler extends SelectionBoxHandler
        implements MouseInputListener {

    private static final Logger logger =
            Logger.getLogger(TextSelectionPageHandler.class.toString());

    /**
     * Tranparencey value used to simulate text highlighting.
     */
    public static final float selectionAlpha = 0.3f;

    // text selection colour
    public static Color selectionColor;

    static {
        // sets the shadow colour of the decorator.
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.text.selectionColor", "#0077FF");
            int colorValue = ColorUtil.convertColor(color);
            selectionColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("0077FF", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading text selection colour");
            }
        }
    }

    // text highlight colour
    public static Color highlightColor;

    static {
        // sets the shadow colour of the decorator.
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.text.highlightColor", "#CC00FF");
            int colorValue = ColorUtil.convertColor(color);
            highlightColor =
                    new Color(colorValue >= 0 ? colorValue :
                            Integer.parseInt("FFF600", 16));
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading text highlight colour");
            }
        }
    }

    // parent page component
    private AbstractPageViewComponent pageViewComponent;
    private DocumentViewController documentViewController;
    private DocumentViewModel documentViewModel;


    /**
     * New Text selection handler.  Make sure to correctly and and remove
     * this mouse and text listeners.
     *
     * @param pageViewComponent page component that this handler is bound to.
     * @param documentViewModel view model.
     */
    public TextSelectionPageHandler(AbstractPageViewComponent pageViewComponent,
                                    DocumentViewModel documentViewModel) {
        this.pageViewComponent = pageViewComponent;
        this.documentViewModel = documentViewModel;
    }

    /**
     * Document view controller callback setup.  Has to be done after the
     * contructor.
     *
     * @param documentViewController document controller callback.
     */
    public void setDocumentViewController(
            DocumentViewController documentViewController) {
        this.documentViewController = documentViewController;
    }

    /**
     * When mouse is double clicked we select the word the mouse if over.  When
     * the mouse is triple clicked we select the line of text that the mouse
     * is over.
     */
    public void mouseClicked(MouseEvent e) {
        // double click we select the whole line.
        if (e.getClickCount() == 3) {
            if (documentViewModel.getViewToolMode() ==
                    DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {

                Page currentPage = pageViewComponent.getPageLock(this);
                // handle text selection mouse coordinates
                Point mouseLocation = (Point) e.getPoint().clone();
                lineSelectHandler(currentPage, mouseLocation);
                pageViewComponent.releasePageLock(currentPage, this);
            }
        }
        // single click we select word that was clicked. 
        else if (e.getClickCount() == 2) {
            if (documentViewModel.getViewToolMode() ==
                    DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {

                Page currentPage = pageViewComponent.getPageLock(this);
                // handle text selection mouse coordinates
                Point mouseLocation = (Point) e.getPoint().clone();
                wordSelectHandler(currentPage, mouseLocation);
                currentPage.getViewText().getSelected();
                pageViewComponent.releasePageLock(currentPage, this);
            }
        }
    }

    public void clearSelection() {
        // on mouse click clear the currently selected sprints
        Page currentPage = pageViewComponent.getPageLock(this);
        // clear selected text.
        if (currentPage.getViewText() != null) {
            currentPage.getViewText().clearSelected();
        }
        pageViewComponent.releasePageLock(currentPage, this);

        // reset painted rectangle
        currentRect = new Rectangle(0, 0, 0, 0);
        updateDrawableRect(pageViewComponent.getWidth(), pageViewComponent.getHeight());

        pageViewComponent.repaint();
    }


    /**
     * Invoked when a mouse button has been pressed on a component.
     */
    public void mousePressed(MouseEvent e) {

        clearSelection();

        // text selection box.
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
            int x = e.getX();
            int y = e.getY();
            currentRect = new Rectangle(x, y, 0, 0);
            updateDrawableRect(pageViewComponent.getWidth(), pageViewComponent.getHeight());
            pageViewComponent.repaint();
        }
    }

    /**
     * Invoked when a mouse button has been released on a component.
     */
    public void mouseReleased(MouseEvent e) {
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
            // update selection rectangle
            updateSelectionSize(e, pageViewComponent);

            // write out selected text.
            if (logger.isLoggable(Level.FINE)) {
                Page currentPage = pageViewComponent.getPageLock(this);
                // handle text selection mouse coordinates
                logger.fine(currentPage.getViewText().getSelected().toString());
                pageViewComponent.releasePageLock(currentPage, this);
            }

            // clear the rectangle
            clearRectangle(pageViewComponent);

            pageViewComponent.repaint();
        }
    }

    /**
     * Invoked when the mouse enters a component.
     */
    public void mouseEntered(MouseEvent e) {

    }

    /**
     * Invoked when the mouse exits a component.
     */
    public void mouseExited(MouseEvent e) {

    }

    /**
     * Invoked when a mouse button is pressed on a component and then
     * dragged.  <code>MOUSE_DRAGGED</code> events will continue to be
     * delivered to the component where the drag originated until the
     * mouse button is released (regardless of whether the mouse position
     * is within the bounds of the component).
     * <p/>
     * Due to platform-dependent Drag&Drop implementations,
     * <code>MOUSE_DRAGGED</code> events may not be delivered during a native
     * Drag&Drop operation.
     */
    public void mouseDragged(MouseEvent e) {

        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {

            // rectangle select tool
            updateSelectionSize(e, pageViewComponent);

            // lock and unlock content before iterating over the pageText tree.
            Page currentPage = pageViewComponent.getPageLock(this);
            multilineSelectHandler(currentPage, e.getPoint());
            pageViewComponent.releasePageLock(currentPage, this);
        }
    }

    public void setSelectionRectangle(Point cursorLocation, Rectangle selection) {
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {

            // rectangle select tool
            setSelectionSize(selection, pageViewComponent);

            // lock and unlock content before iterating over the pageText tree.
            Page currentPage = pageViewComponent.getPageLock(this);
            multilineSelectHandler(currentPage, cursorLocation);
            pageViewComponent.releasePageLock(currentPage, this);
        }
    }

    /**
     * Invoked when the mouse cursor has been moved onto a component
     * but no buttons have been pushed.
     */
    public void mouseMoved(MouseEvent e) {
        // change state of mouse from pointer to text selection icon
        if (documentViewModel.getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
            Page currentPage = pageViewComponent.getPageLock(this);
            selectionMouseCursor(currentPage, e.getPoint());
            pageViewComponent.releasePageLock(currentPage, this);
        }
    }

    /**
     * Utility for detecting and changing the cursor to the text selection tool
     * when over text in the doucument.
     *
     * @param currentPage   page to looking for text inersection on.
     * @param mouseLocation location of mouse.
     */
    private void selectionMouseCursor(Page currentPage, Point mouseLocation) {
        if (currentPage != null &&
                currentPage.isInitiated()) {
            // get page text
            PageText pageText = currentPage.getViewText();
            if (pageText != null) {

                // get page transform, same for all calculations
                AffineTransform pageTransform = currentPage.getPageTransform(
                        Page.BOUNDARY_CROPBOX,
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());

                ArrayList<LineText> pageLines = pageText.getPageLines();
                boolean found = false;
                Point2D.Float pageMouseLocation =
                        convertMouseToPageSpace(mouseLocation, pageTransform);
                for (LineText pageLine : pageLines) {
                    // check for containment, if so break into words.
                    if (pageLine.getBounds().contains(pageMouseLocation)) {
                        found = true;
                        documentViewController.setViewCursor(
                                DocumentViewController.CURSOR_TEXT_SELECTION);
                        break;
                    }
                }
                if (!found) {
                    documentViewController.setViewCursor(
                            DocumentViewController.CURSOR_SELECT);
                }
            }
        }
    }

    /**
     * Convert the mouse cooridates to the space specified by the pageTransform
     * matrix.  This is a utility method for conveting the mouse coordinates
     * to page space so that it can be used in a contains calculation for text
     * selection.
     *
     * @param mousePoint    point to convert space of
     * @param pageTransform tranform
     * @return page space mouse coordinates.
     */
    private Point2D.Float convertMouseToPageSpace(Point mousePoint,
                                                  AffineTransform pageTransform) {
        Point2D.Float pageMouseLocation = new Point2D.Float();
        try {
            pageTransform.createInverse().transform(
                    mousePoint, pageMouseLocation);
        } catch (NoninvertibleTransformException e) {
            logger.log(Level.SEVERE,
                    "Error converting mouse point to page space.", e);
        }
        return pageMouseLocation;
    }

    /**
     * Converts the rectangle to the space specified by the page tranform. This
     * is a utility method for converting a selection rectangle to page space
     * so that an intersection can be calculated to determine a selected state.
     *
     * @param mouseRect     rectangle to convert space of
     * @param pageTransform page transform
     * @return converted rectangle.
     */
    private Rectangle2D convertRectangleToPageSpace(Rectangle mouseRect,
                                                    AffineTransform pageTransform) {
        GeneralPath shapePath;
        try {
            AffineTransform tranform = pageTransform.createInverse();
            shapePath = new GeneralPath(mouseRect);
            shapePath.transform(tranform);
            return shapePath.getBounds2D();
        } catch (NoninvertibleTransformException e) {
            logger.log(Level.SEVERE,
                    "Error converting mouse point to page space.", e);
        }
        return null;
    }

    /**
     * Utility for selecting multiple lines via l-> right type select. This
     * method should only be called from within a locked page content
     *
     * @param currentPage   page to looking for text inersection on.
     * @param mouseLocation location of mouse.
     */
    private void multilineSelectHandler(Page currentPage, Point mouseLocation) {

        if (currentPage != null &&
                currentPage.isInitiated()) {
            // get page text
            PageText pageText = currentPage.getViewText();
            if (pageText != null) {

                // clear the currently selected state, ignore highlighted.
                currentPage.getViewText().clearSelected();

                // get page transform, same for all calculations
                AffineTransform pageTransform = currentPage.getPageTransform(
                        Page.BOUNDARY_CROPBOX,
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());
                LineText firstPageLine = null;
                Point2D.Float pageMouseLocation =
                        convertMouseToPageSpace(mouseLocation, pageTransform);
                Rectangle2D pageRectToDraw =
                        convertRectangleToPageSpace(rectToDraw, pageTransform);
                ArrayList<LineText> pageLines = pageText.getPageLines();
                for (LineText pageLine : pageLines) {
                    // check for containment, if so break into words.
                    if (pageLine.intersects(pageRectToDraw)) {
                        pageLine.setHasSelected(true);
                        if (firstPageLine == null) {
                            firstPageLine = pageLine;
                        }
                        if (pageLine.getBounds().contains(pageMouseLocation)) {

                            ArrayList<WordText> lineWords = pageLine.getWords();
                            for (WordText word : lineWords) {
                                if (word.intersects(pageRectToDraw)) {
                                    word.setHasHighlight(true);
                                    ArrayList<GlyphText> glyphs = word.getGlyphs();
                                    for (GlyphText glyph : glyphs) {
                                        if (glyph.intersects(pageRectToDraw)) {
                                            glyph.setSelected(true);
                                            pageViewComponent.repaint();
                                        }
                                    }
                                }
                            }
                        } else if (firstPageLine == pageLine) {
                            // left to right selection
//                            if (currentRect.width > 0 ){
                            selectLeftToRight(pageLine, pageTransform);
//                            }else{
//                                selectRightToLeft(pageLine, pageTransform););
//                            }
                        } else {
                            pageLine.selectAll();
                        }
                    }
                }
            }
        }
    }

    /**
     * Utility for right to left selection, NOT Correct
     *
     * @param pageLine      page line to select.
     * @param pageTransform page transform.
     */
    private void selectRightToLeft(LineText pageLine,
                                   AffineTransform pageTransform) {
//        ArrayList<WordText> lineWords = pageLine.getWords();
//        Rectangle2D pageRectToDraw =
//                        convertRectangleToPageSpace(rectToDraw, pageTransform);
//        for (WordText word : lineWords) {
//            if (word.intersects(pageRectToDraw)) {
//                word.setHasHighlight(true);
//                ArrayList<GlyphText> glyphs = word.getGlyphs();
//                GlyphText glyph = null;
//                for (int i = glyphs.size() - 1; i >= 0; i--) {
//                    if (glyph.intersects(pageRectToDraw)) {
//                        glyph.setSelected(true);
//                        pageViewComponent.repaint();
//                    }
//                }
//            }
//        }
    }


    /**
     * Simple left to right, top down type selection model, not perfect.
     *
     * @param pageLine      page line to select.
     * @param pageTransform page transform.
     */
    private void selectLeftToRight(LineText pageLine,
                                   AffineTransform pageTransform) {
        GlyphText fistGlyph = null;
        Rectangle2D pageRectToDraw =
                convertRectangleToPageSpace(rectToDraw, pageTransform);
        ArrayList<WordText> lineWords = pageLine.getWords();
        for (WordText word : lineWords) {
            if (word.intersects(pageRectToDraw)) {
                word.setHasHighlight(true);
                ArrayList<GlyphText> glyphs = word.getGlyphs();
                for (GlyphText glyph : glyphs) {
                    if (glyph.intersects(pageRectToDraw)) {
                        if (fistGlyph == null) {
                            fistGlyph = glyph;
                        }
                        glyph.setSelected(true);
                    } else if (fistGlyph != null) {
                        glyph.setSelected(true);
                    }
                }
            }
            // select the rest
            else if (fistGlyph != null) {
                word.selectAll();
            }
        }
        pageViewComponent.repaint();
    }

    /**
     * Utility for selecting multiple lines via rectangle like tool. The
     * selection works based on the intersection of the rectangle and glyph
     * bounding box.
     * <p/>
     * This method should only be called from within a locked page content
     *
     * @param currentPage   page to looking for text inersection on.
     * @param mouseLocation location of mouse.
     */
    private void rectangleSelectHandler(Page currentPage, Point mouseLocation) {
        // detect L->R or R->L
        if (currentPage != null &&
                currentPage.isInitiated()) {
            // get page text
            PageText pageText = currentPage.getViewText();
            if (pageText != null) {

                // clear the currently selected state, ignore highlighted.
                currentPage.getViewText().clearSelected();

                // get page transform, same for all calculations
                AffineTransform pageTransform = currentPage.getPageTransform(
                        Page.BOUNDARY_CROPBOX,
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());

                Rectangle2D pageRectToDraw =
                        convertRectangleToPageSpace(rectToDraw, pageTransform);

                ArrayList<LineText> pageLines = pageText.getPageLines();
                for (LineText pageLine : pageLines) {
                    // check for containment, if so break into words.
                    if (pageLine.intersects(pageRectToDraw)) {
                        pageLine.setHasSelected(true);
                        ArrayList<WordText> lineWords = pageLine.getWords();
                        for (WordText word : lineWords) {
                            if (word.intersects(pageRectToDraw)) {
                                word.setHasHighlight(true);
                                ArrayList<GlyphText> glyphs = word.getGlyphs();
                                for (GlyphText glyph : glyphs) {
                                    if (glyph.intersects(pageRectToDraw)) {
                                        glyph.setSelected(true);
                                        pageViewComponent.repaint();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Utility for selecting multiple lines via rectangle like tool. The
     * selection works based on the intersection of the rectangle and glyph
     * bounding box.
     * <p/>
     * This method should only be called from within a locked page content
     *
     * @param currentPage   page to looking for text inersection on.
     * @param mouseLocation location of mouse.
     */
    private void wordSelectHandler(Page currentPage, Point mouseLocation) {

        if (currentPage != null &&
                currentPage.isInitiated()) {
            // get page text
            PageText pageText = currentPage.getViewText();
            if (pageText != null) {

                // clear the currently selected state, ignore highlighted.
                currentPage.getViewText().clearSelected();

                // get page transform, same for all calculations
                AffineTransform pageTransform = currentPage.getPageTransform(
                        Page.BOUNDARY_CROPBOX,
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());

                Point2D.Float pageMouseLocation =
                        convertMouseToPageSpace(mouseLocation, pageTransform);
                ArrayList<LineText> pageLines = pageText.getPageLines();
                for (LineText pageLine : pageLines) {
                    // check for containment, if so break into words.
                    if (pageLine.getBounds().contains(pageMouseLocation)) {
                        pageLine.setHasSelected(true);
                        ArrayList<WordText> lineWords = pageLine.getWords();
                        for (WordText word : lineWords) {
//                            if (word.contains(pageTransform, mouseLocation)) {
                            if (word.getBounds().contains(pageMouseLocation)) {
                                word.selectAll();
                                pageViewComponent.repaint();
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Utility for selecting a LineText which is usually a sentence in the
     * document.   This is usually triggered by a tripple click of the mouse
     *
     * @param currentPage   page to select
     * @param mouseLocation location of mouse
     */
    private void lineSelectHandler(Page currentPage, Point mouseLocation) {
        if (currentPage != null &&
                currentPage.isInitiated()) {
            // get page text
            PageText pageText = currentPage.getViewText();
            if (pageText != null) {

                // clear the currently selected state, ignore highlighted.
                currentPage.getViewText().clearSelected();

                // get page transform, same for all calculations
                AffineTransform pageTransform = currentPage.getPageTransform(
                        Page.BOUNDARY_CROPBOX,
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());

                Point2D.Float pageMouseLocation =
                        convertMouseToPageSpace(mouseLocation, pageTransform);
                ArrayList<LineText> pageLines = pageText.getPageLines();
                for (LineText pageLine : pageLines) {
                    // check for containment, if so break into words.
                    if (pageLine.getBounds().contains(pageMouseLocation)) {
                        pageLine.selectAll();
                        pageViewComponent.repaint();
                        break;
                    }
                }
            }
        }
    }

    /**
     * Utility for painting the highlight and selected
     *
     * @param g graphics to paint to.
     */
    public void paintSelectedText(Graphics g) {
        // ready outline paint
        Graphics2D gg = (Graphics2D) g;
        AffineTransform prePaintTransform = gg.getTransform();
        Color oldColor = gg.getColor();
        Stroke oldStroke = gg.getStroke();
        gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                selectionAlpha));
        gg.setColor(selectionColor);
        gg.setStroke(new BasicStroke(1.0f));

        Page currentPage = pageViewComponent.getPageLock(this);
        if (currentPage != null && currentPage.isInitiated()) {
            PageText pageText = currentPage.getViewText();
            if (pageText != null) {
                // get page transformation
                AffineTransform pageTransform = currentPage.getPageTransform(
                        documentViewModel.getPageBoundary(),
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());
                // paint the sprites
                GeneralPath textPath;
                for (LineText lineText : pageText.getPageLines()) {
                    for (WordText wordText : lineText.getWords()) {
                        // paint whole word
                        if (wordText.isSelected() || wordText.isHighlighted()) {
                            textPath = new GeneralPath(wordText.getBounds());
                            textPath.transform(pageTransform);
                            // paint highlight over any selected
                            if (wordText.isSelected()) {
                                gg.setColor(selectionColor);
                                gg.fill(textPath);
                            }
                            if (wordText.isHighlighted()) {
                                gg.setColor(highlightColor);
                                gg.fill(textPath);
                            }
                        }
                        // check children
                        else {
                            for (GlyphText glyph : wordText.getGlyphs()) {
                                if (glyph.isSelected()) {
                                    textPath = new GeneralPath(glyph.getBounds());
                                    textPath.transform(pageTransform);
                                    gg.setColor(selectionColor);
                                    gg.fill(textPath);
                                }
                            }
                        }
                    }
                }
            }
        }
        pageViewComponent.releasePageLock(currentPage, this);

        // pain selection box
        paintSelectionBox(g);

        // restore graphics state to where we left it. 
        gg.setTransform(prePaintTransform);
        gg.setStroke(oldStroke);
        gg.setColor(oldColor);

        // paint words for bounds test.
//        paintTextBounds(g);

    }


    /**
     * Utility for painting text bounds.
     *
     * @param g graphics context to paint to.
     */
    private void paintTextBounds(Graphics g) {
        Page currentPage = pageViewComponent.getPageLock(this);
        // get page transformation
        AffineTransform pageTransform = currentPage.getPageTransform(
                documentViewModel.getPageBoundary(),
                documentViewModel.getViewRotation(),
                documentViewModel.getViewZoom());
        Graphics2D gg = (Graphics2D) g;
        Color oldColor = g.getColor();
        g.setColor(Color.red);

        PageText pageText = currentPage.getViewText();
        ArrayList<LineText> pageLines = pageText.getPageLines();
        for (LineText lineText : pageLines) {

            for (WordText wordText : lineText.getWords()) {
                for (GlyphText glyph : wordText.getGlyphs()) {
                    g.setColor(Color.black);
                    GeneralPath glyphSpritePath =
                            new GeneralPath(glyph.getBounds());
                    glyphSpritePath.transform(pageTransform);
                    gg.draw(glyphSpritePath);
                }

//                if (!wordText.isWhiteSpace()) {
//                    g.setColor(Color.blue);
//                    GeneralPath glyphSpritePath =
//                            new GeneralPath(wordText.getBounds());
//                    glyphSpritePath.transform(pageTransform);
//                    gg.draw(glyphSpritePath);
//                }
            }
            g.setColor(Color.red);
            GeneralPath glyphSpritePath =
                    new GeneralPath(lineText.getBounds());
            glyphSpritePath.transform(pageTransform);
            gg.draw(glyphSpritePath);
        }
        g.setColor(oldColor);
        pageViewComponent.releasePageLock(currentPage, this);
    }


}
