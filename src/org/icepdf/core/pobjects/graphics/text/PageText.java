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
package org.icepdf.core.pobjects.graphics.text;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;

/**
 * Page text represents the root element of a page's text hierarchy which
 * looks something like this.
 * <ul>
 * PageText -&gt; LineText* -&gt; WordText* -&gt; GlyphText*
 * </ul>
 * The hierarchy elements are build by the content parser when text extraction
 * is enabled.  It is build to seperate the huristics used to calculate
 * word and line detection which is used for text extraction/search,
 * search highlighting and text highlighting.
 * <p/>
 * It very important to note that all coordinates system represented in this
 * hierarchy of object has been normalized to the page space.  This allows for
 * object to be sorted and drawn. Also this structure is not used for page
 * layout and painting.  It is is used for painting text selectin via UI input
 * or search.  The seperation is needed so that the text represented in Page
 * text can be padded and sorted to aid in text extraction readability.
 *
 * @since 4.0
 */
public class PageText implements TextSelect {

    // pointer to current line during document parse, no other use.
    private LineText currentLine;

    private ArrayList<LineText> pageLines;

    public PageText() {
        pageLines = new ArrayList<LineText>(50);
    }

    public void newLine() {
        // make sure we don't insert a new line if the previous has no words. 
        if (currentLine != null &&
                currentLine.getWords().size() == 0){
            return;
        }
        currentLine = new LineText();
        pageLines.add(currentLine);
    }

    public void addGlyph(GlyphText sprite) {
        if (currentLine == null) {
            newLine();
        }
        currentLine.addText(sprite);
    }

    public ArrayList<LineText> getPageLines() {
        return pageLines;
    }

    /**
     * Utility method to normalize text created in a Xform content stream
     * and is only called from the contentParser when parsing 'Do' token.  
     *
     * @param transform do matrix tranform
     */
    public void applyXObjectTransform(AffineTransform transform) {
        for (LineText lineText : pageLines) {
            lineText.clearBounds();
            for (WordText wordText : lineText.getWords()) {
                wordText.clearBounds();
                for (GlyphText glyph : wordText.getGlyphs()) {
                    glyph.normalizeToUserSpace(transform);
                }
            }
        }
    }

    public void clearSelected(){
        for (LineText lineText : pageLines) {
            lineText.clearSelected();
        }
    }

    public void clearHighlighted(){
        for (LineText lineText : pageLines) {
            lineText.clearHighlighted();
        }
    }

    public StringBuilder getSelected() {
        StringBuilder selectedText = new StringBuilder();
        for (LineText lineText : pageLines) {
            selectedText.append(lineText.getSelected());
        }
        return selectedText;
    }

    public void selectAll() {
        for (LineText lineText : pageLines) {
            lineText.selectAll();
        }
    }

     public void deselectAll() {
        for (LineText lineText : pageLines) {
            lineText.clearSelected();
        }
    }

    public void dispose() {
        if (pageLines != null) {
            pageLines.clear();
            pageLines.trimToSize();
        }
    }

    public String toString() {
        StringBuilder extractedText = new StringBuilder();
        for (LineText lineText : pageLines) {

            for (WordText wordText : lineText.getWords()) {
                extractedText.append(wordText.getText());
            }
            extractedText.append('\n');
        }
        return extractedText.toString();
    }

}
