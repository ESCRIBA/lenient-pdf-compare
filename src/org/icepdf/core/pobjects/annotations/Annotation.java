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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.actions.Action;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.util.ContentParser;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>An <code>Annotation</code> class associates an object such as a note, sound, or movie with
 * a location on a page of a PDF document, or provides a way to interact with
 * the user by means of the mouse and keyboard.</p>
 * <p/>
 * <p>This class allows direct access to the a Annotations dictionary.
 * Developers can take advantage of this information as they see fit.  It is
 * important to note that an annotations' rectangle coordinates are defined
 * in the PDF document space.  In order to map the rectangle coordinates to
 * a view, they must be converted from the Cartesian plain to the the Java2D
 * plain.  The PageView method getPageBounds() can be used to locate the position
 * of a page within its parent component.</p>
 * <p/>
 * Base class of all the specific Annotation types
 * <p/>
 * Taken from the PDF 1.6 spec, here is some relevant documentation,
 * along with some additional commentary
 * <p/>
 * <h2>8.4.1 Annotation Dictionaries</h2>
 * <table border=1>
 * <tr>
 * <td>Key</td>
 * <td>Type</td>
 * <td>Value</td>
 * </tr>
 * <tr>
 * <td><b>Type</b></td>
 * <td>name</td>
 * <td>(<i>Optional</i>) The type of PDF object that this dictionary describes;
 * if present, must be <b>Annot</b> for an annotation dictionary.</td>
 * </tr>
 * <tr>
 * <td><b>Subtype</b></td>
 * <td>name</td>
 * <td>(<i>Required</i>) The type of annotation that this dictionary describes.</td>
 * </tr>
 * <tr>
 * <td><b>Rect</b></td>
 * <td>rectangle</td>
 * <td>(<i>Required</i>) The <i>annotation rectangle</i>, defining the location of the
 * annotation on the page in default user space units.</td>
 * <td>getUserspaceLocation()</td>
 * </tr>
 * <tr>
 * <td><b>Contents</b></td>
 * <td>text string</td>
 * <td>(<i>Optional</i>) Text to be displayed for the annotation or, if this type of annotation
 * does not display text, an alternate description of the annotation's contents
 * in human-readable form.'s contents in support of accessibility to users with
 * disabilities or for other purposes (see Section 10.8.2, "Alternate Descriptions").
 * See Section 8.4.5, "Annotation Types" for more details on the meaning
 * of this entry for each annotation type.</td>
 * </tr>
 * <tr>
 * <td><b>P</b></td>
 * <td>dictionary</td>
 * <td>(<i>Optional; PDF 1.3; not used in FDF files</i>) An indirect reference to the page
 * object with which this annotation is associated.</td>
 * </tr>
 * <tr>
 * <td><b>NM</b></td>
 * <td>text string</td>
 * <td>(<i>Optional; PDF 1.4</i>) The <i>annotation name</i>, a text string uniquely identifying it
 * among all the annotations on its page.</td>
 * </tr>
 * <tr>
 * <td><b>M</b></td>
 * <td>date or string</td>
 * <td>(<i>Optional; PDF 1.1</i>) The date and time when the annotation was most
 * recently modified. The preferred format is a date string as described in Section
 * 3.8.3, "Dates," but viewer applications should be prepared to accept and
 * display a string in any format. (See implementation note 78 in Appendix H.)</td>
 * </tr>
 * <tr>
 * <td><b>F</b></td>
 * <td>integer</td>
 * <td>(<i>Optional; PDF 1.1</i>) A set of flags specifying various characteristics of the annotation
 * (see Section 8.4.2, "Annotation Flags"). Default value: 0.</td>
 * </tr>
 * <tr>
 * <td><b>BS</b></td>
 * <td>dictionary</td>
 * <td>(<i>Optional; PDF 1.2</i>) A border style dictionary specifying the characteristics of
 * the annotation's border (see Section 8.4.3, "Border Styles"; see also implementation
 * notes 79 and 86 in Appendix H).<br>
 * <br>
 * <i><b>Note:</b> This entry also specifies the width and dash pattern for the lines drawn by
 * line, square, circle, and ink annotations. See the note under <b>Border</b> (below) for
 * additional information.</i><br>
 * <br>
 * Table 8.13 summarizes the contents of the border style dictionary. If neither
 * the <b>Border</b> nor the <b>BS</b> entry is present, the border is drawn as a solid line with a
 * width of 1 point.</td>
 * </tr>
 * <tr>
 * <td><b>AP</b></td>
 * <td>dictionary</td>
 * <td>(<i>Optional; PDF 1.2</i>) An <i>appearance dictionary</i> specifying how the annotation
 * is presented visually on the page (see Section 8.4.4, "Appearance Streams" and
 * also implementation notes 79 and 80 in Appendix H). Individual annotation
 * handlers may ignore this entry and provide their own appearances.<br>
 * <br>
 * For convenience in managing appearance streams that are used repeatedly, the AP
 * entry in a PDF document's name dictionary (see Section 3.6.3, "Name Dictionary")
 * can contain a name tree mapping name strings to appearance streams. The
 * name strings have no standard meanings; no PDF objects refer to appearance
 * streams by name.</td>
 * </tr>
 * <tr>
 * <td><b>AS</b></td>
 * <td>name</td>
 * <td>(<i>Required if the appearance dictionary <b>AP</b> contains one or more subdictionaries;
 * PDF 1.2</i>) The annotation's <i>appearance state</i>, which selects the applicable
 * appearance stream from an appearance subdictionary (see Section 8.4.4, "Appearance
 * Streams" and also implementation note 79 in Appendix H).</td>
 * </tr>
 * <tr>
 * <td><b>Border</b></td>
 * <td>array</td>
 * <td>(<i>Optional</i>) An array specifying the characteristics of the annotation's border.
 * The border is specified as a rounded rectangle.<br>
 * <br>
 * In PDF 1.0, the array consists of three numbers defining the horizontal corner
 * radius, vertical corner radius, and border width, all in default user space
 * units. If the corner radii are 0, the border has square (not rounded) corners; if
 * the border width is 0, no border is drawn. (See implementation note 81 in
 * Appendix H.) <br>
 * <br>
 * In PDF 1.1, the array may have a fourth element, an optional <i>dash array</i>
 * defining a pattern of dashes and gaps to be used in drawing the border. The
 * dash array is specified in the same format as in the line dash pattern parameter
 * of the graphics state (see "Line Dash Pattern" on page 187). For example, a
 * <b>Border</b> value of [0 0 1 [3 2]] specifies a border 1 unit wide, with square corners,
 * drawn with 3-unit dashes alternating with 2-unit gaps. Note that no
 * dash phase is specified; the phase is assumed to be 0. (See implementation
 * note 82 in Appendix H.)<br>
 * <br>
 * <i><b>Note:</b> In PDF 1.2 or later, this entry may be ignored in favor of the <b>BS</b>
 * entry (see above); see implementation note 86 in Appendix H.</i><br>
 * <br>
 * Default value: [0 0 1].</td>
 * </tr>
 * <tr>
 * <td><b>BE</b></td>
 * <td>dictionary</td>
 * <td>(<i>Optional; PDF 1.5</i>) Some annotations (square, circle, and polygon) may
 * have a <b>BE</b> entry, which is a <i>border effect</i> dictionary that specifies an effect
 * to be applied to the border of the annotations. Its entries are listed in Table 8.14.</td>
 * </tr>
 * <tr>
 * <td><b>C</b></td>
 * <td>array</td>
 * <td>(<i>Optional; PDF 1.1</i>) An array of three numbers in the range 0.0 to 1.0, representing
 * the components of a color in the <b>DeviceRGB</b> color space. This color is
 * used for the following purposes:
 * <ul>
 * <li>The background of the annotation's icon when closed
 * <li>The title bar of the annotation's pop-up window
 * <li>The border of a link annotation
 * </ul></td>
 * </tr>
 * <tr>
 * <td><b>A</b></td>
 * <td>dictionary</td>
 * <td>(<i>Optional; PDF 1.1</i>) An action to be performed when the annotation is activated
 * (see Section 8.5, "Actions").<br>
 * <br>
 * <i><b>Note:</b> This entry is not permitted in link annotations if a Dest entry is present
 * (see "Link Annotations" on page 587). Also note that the A entry in movie annotations
 * has a different meaning (see "Movie Annotations" on page 601).</i></td>
 * </tr>
 * <tr>
 * <td><b>AA</b></td>
 * <td>dictionary</td>
 * <td>(<i>Optional; PDF 1.2</i>) An additional-actions dictionary defining the annotation's
 * behavior in response to various trigger events (see Section 8.5.2,
 * "Trigger Events"). At the time of publication, this entry is used only by widget
 * annotations.</td>
 * </tr>
 * <tr>
 * <td><b>StructParent</b></td>
 * <td>integer</td>
 * <td>(<i>(Required if the annotation is a structural content item; PDF 1.3</i>) The integer
 * key of the annotation's entry in the structural parent tree (see "Finding Structure
 * Elements from Content Items" on page 797).</td>
 * </tr>
 * <tr>
 * <td><b>OC</b></td>
 * <td>dictionary</td>
 * <td>(<i>Optional; PDF 1.5</i>) An optional content group or optional content membership
 * dictionary (see Section 4.10, "Optional Content") specifying the optional
 * content properties for the annotation. Before the annotation is drawn, its visibility
 * is determined based on this entry as well as the annotation flags specified
 * in the <b>F</b> entry (see Section 8.4.2, "Annotation Flags"). If it is determined
 * to be invisible, the annotation is skipped, as if it were not in the document.</td>
 * </tr>
 * </table>
 * <p/>
 * <p/>
 * <h2>8.4.2 Annotation Flags</h2>
 * The value of the annotation dictionary's <b>F</b> entry is an unsigned 32-bit integer containing
 * flags specifying various characteristics of the annotation. Bit positions
 * within the flag word are numbered from 1 (low-order) to 32 (high-order). Table
 * 8.12 shows the meanings of the flags; all undefined flag bits are reserved and must
 * be set to 0.
 * <table border=1>
 * <tr>
 * <td>Bit position</td>
 * <td>Name</td>
 * <td>Meaning</td>
 * </tr>
 * <tr>
 * <td>1</td>
 * <td>Invisible</td>
 * <td>If set, do not display the annotation if it does not belong to one of the standard
 * annotation types and no annotation handler is available. If clear, display such an
 * unknown annotation using an appearance stream specified by its appearance
 * dictionary, if any (see Section 8.4.4, "Appearance Streams").</td>
 * </tr>
 * <tr>
 * <td>2</td>
 * <td>Hidden</td>
 * <td>If set, do not display or print the annotation or allow it to interact
 * with the user, regardless of its annotation type or whether an annotation
 * handler is available. In cases where screen space is limited, the ability to hide
 * and show annotations selectively can be used in combination with appearance
 * streams (see Section 8.4.4, "Appearance Streams") to display auxiliary pop-up
 * information similar in function to online help systems. (See implementation
 * note 83 in Appendix H.)</td>
 * </tr>
 * <tr>
 * <td>3</td>
 * <td>Print</td>
 * <td>If set, print the annotation when the page is printed. If clear, never
 * print the annotation, regardless of whether it is displayed on the screen. This
 * can be useful, for example, for annotations representing interactive pushbuttons,
 * which would serve no meaningful purpose on the printed page. (See
 * implementation note 83 in Appendix H.)</td>
 * </tr>
 * <tr>
 * <td>4</td>
 * <td>NoZoom</td>
 * <td>If set, do not scale the annotation's appearance to match the magnification
 * of the page. The location of the annotation on the page (defined by the
 * upper-left corner of its annotation rectangle) remains fixed, regardless of the
 * page magnification. See below for further discussion.</td>
 * </tr>
 * <tr>
 * <td>5</td>
 * <td>NoRotate</td>
 * <td>If set, do not rotate the annotation's appearance to match the rotation
 * of the page. The upper-left corner of the annotation rectangle remains in a fixed
 * location on the page, regardless of the page rotation. See below for further discussion.</td>
 * </tr>
 * <tr>
 * <td>6</td>
 * <td>NoView</td>
 * <td>If set, do not display the annotation on the screen or allow it to
 * interact with the user. The annotation may be printed (depending on the setting
 * of the Print flag) but should be considered hidden for purposes of on-screen
 * display and user interaction.</td>
 * </tr>
 * <tr>
 * <td>7</td>
 * <td>ReadOnly</td>
 * <td>If set, do not allow the annotation to interact with the user. The
 * annotation may be displayed or printed (depending on the settings of the
 * NoView and Print flags) but should not respond to mouse clicks or change its
 * appearance in response to mouse motions.<br>
 * <br>
 * <i><b>Note:</b> This flag is ignored for widget annotations; its function is subsumed by the
 * ReadOnly flag of the associated form field (see Table 8.66 on page 638).</i></td>
 * </tr>
 * <tr>
 * <td>8</td>
 * <td>Locked</td>
 * <td>If set, do not allow the annotation to be deleted or its properties (including
 * position and size) to be modified by the user. However, this flag does
 * not restrict changes to the annotation's contents, such as the value of a form
 * field. (See implementation note 84 in Appendix H.)</td>
 * </tr>
 * <tr>
 * <td>9</td>
 * <td>ToggleNoView</td>
 * <td>If set, invert the interpretation of the NoView flag for certain events. A
 * typical use is to have an annotation that appears only when a mouse cursor is
 * held over it; see implementation note 85 in Appendix H.</td>
 * </tr>
 * </table>
 *
 * @author Mark Collette
 * @since 2.5
 */

public class Annotation extends Dictionary {

    private static final Logger logger =
            Logger.getLogger(Annotation.class.toString());

    /**
     * Dictionary constants for Annotations.
     */

    public static final Name TYPE_VALUE = new Name("Annot");

    /**
     * Annotation subtype and types.
     */
    public static final Name SUBTYPE_LINK = new Name("Link");
    public static final Name SUBTYPE_LINE = new Name("Line");
    public static final Name SUBTYPE_SQUARE = new Name("Square");
    public static final Name SUBTYPE_CIRCLE = new Name("Circle");
    public static final Name SUBTYPE_POLYGON = new Name("Polygon");
    public static final Name SUBTYPE_POLYLINE = new Name("Polyline");
    public static final Name SUBTYPE_HIGHLIGHT = new Name("Highlight");

    /**
     * Border style
     */
    public static final Name BORDER_STYLE_KEY = new Name("BS");

    /**
     * The annotation location on the page in user space units.
     */
    public static final Name RECTANGLE_KEY = new Name("Rect");

    /**
     * The action to be performed whenteh annotation is activated.
     */
    public static final Name ACTION_KEY = new Name("A");

    /**
     * Page that this annotation is associated with.
     */
    public static final Name PARENT_PAGE_KEY = new Name("P");

    /**
     * Annotation border characteristics.
     */
    public static final Name BORDER_KEY = new Name("Border");

    /**
     * Annotation border characteristics.
     */
    public static final Name FLAG_KEY = new Name("F");

    /**
     * RGB colour value for background, titlebars and link annotation borders
     */
    public static final Name COLOR_KEY = new Name("C");


    /**
     * Appearance dictionary specifying how the annotation is presented
     * visually on the page.
     */
    public static final Name APPEARANCE_STREAM_KEY = new Name("AP");

    /**
     * Appearance state selecting default from multiple AP's.
     */
    public static final Name APPEARANCE_STATE_KEY = new Name("AS");

    /**
     * Appearance dictionary specifying how the annotation is presented
     * visually on the page for normal display.
     */
    public static final Name APPEARANCE_STREAM_NORMAL_KEY = new Name("N");

    /**
     * Appearance dictionary specifying how the annotation is presented
     * visually on the page for rollover display.
     */
    public static final Name APPEARANCE_STREAM_ROLLOVER_KEY = new Name("R");

    /**
     * Appearance dictionary specifying how the annotation is presented
     * visually on the page for down display.
     */
    public static final Name APPEARANCE_STREAM_DOWN_KEY = new Name("d");

    /**
     * Border property indexes for the border vector,  only applicable
     * if the border style has not been set.
     */
    public static final int BORDER_HORIZONTAL_CORNER_RADIUS = 0;
    public static final int BORDER_VERTICAL_CORNER_RADIUS = 1;
    public static final int BORDER_WIDTH = 2;
    public static final int BORDER_DASH = 3;

    /**
     * Annotion may or may not have a visible rectangle border
     */
    public static final int VISIBLE_RECTANGLE = 1;
    public static final int INVISIBLE_RECTANGLE = 0;

    // type of annotation
    protected Name subtype;
    // borders style of the annotation, can be null
    protected BorderStyle borderStyle;
    // border defined by vector
    protected Vector<Number> border;
    // border color of annotation.
    protected Color color;
    // annotation bounding rectangle in user space.
    protected Rectangle2D.Float userSpaceRectangle;
    // test for borderless annotation types
    protected boolean canDrawBorder;

    /**
     * Should only be called from Parser,  Use AnnotationFactory if you
     * creating a new annotation.
     *
     * @param library   document library
     * @param hashTable annotation properties.
     * @return annotation instance.
     */
    public static Annotation buildAnnotation(Library library, Hashtable hashTable) {
        Annotation annot = null;
        Name subtype = (Name) hashTable.get(SUBTYPE_KEY);
        if (subtype != null) {
            if (subtype.equals(SUBTYPE_LINK))
                annot = new LinkAnnotation(library, hashTable);
        }
        if (annot == null) {
            annot = new Annotation(library, hashTable);
        }
        return annot;
    }

    /**
     * Creates a new instance of an Annotation.
     *
     * @param l document library.
     * @param h dictionary entries.
     */
    public Annotation(Library l, Hashtable h) {
        super(l, h);
        // type of Annotation
        subtype = (Name) getObject(SUBTYPE_KEY);

        // no borders for the followING types,  not really in the
        // spec for some reason, Acrobat doesn't render them.
        canDrawBorder = !(SUBTYPE_LINE.equals(subtype) ||
                SUBTYPE_CIRCLE.equals(subtype) ||
                SUBTYPE_SQUARE.equals(subtype) ||
                SUBTYPE_POLYGON.equals(subtype) ||
                SUBTYPE_POLYLINE.equals(subtype));

        // parse out border style if available
        Hashtable BS = (Hashtable) getObject(BORDER_STYLE_KEY);
        if (BS != null) {
            borderStyle = new BorderStyle(library, BS);
        }
        // get old school border
        Object borderObject = getObject(BORDER_KEY);
        if (borderObject != null && borderObject instanceof Vector) {
            border = (Vector<Number>) borderObject;
        }

        // parse out border colour, specific to link annotations.
        color = Color.black; // we default to black but probably should be null
        Vector C = (Vector) getObject(COLOR_KEY);
        // parse thought rgb colour.
        if (C != null && C.size() >= 3) {
            float red = ((Number) C.get(0)).floatValue();
            float green = ((Number) C.get(1)).floatValue();
            float blue = ((Number) C.get(2)).floatValue();
            red = Math.max(0.0f, Math.min(1.0f, red));
            green = Math.max(0.0f, Math.min(1.0f, green));
            blue = Math.max(0.0f, Math.min(1.0f, blue));
            color = new Color(red, green, blue);
        }
    }

    /**
     * Gets the type of annotation that this dictionary describes.
     * For compatibility with the old org.icepdf.core.pobjects.Annotation.getSubType()
     *
     * @return subtype of annotation
     */
    public String getSubType() {
        return library.getName(entries, SUBTYPE_KEY.getName());
    }

    /**
     * Gets the annotation rectangle, and defines the location of the annotation on
     * the page in default user space units.
     * For compatibility with the old org.icepdf.core.pobjects.Annotation.getRectangle()
     *
     * @return rectangle of annotation
     */
    public Rectangle2D.Float getUserSpaceRectangle() {
        if (userSpaceRectangle == null) {
            Object tmp = getObject(RECTANGLE_KEY);
            if (tmp instanceof Vector) {
                userSpaceRectangle = library.getRectangle(entries, RECTANGLE_KEY.getName());
            }
        }
        return userSpaceRectangle;
    }

    /**
     * Sets the users page rectangle for this annotation action instance
     */
    public void setUserSpaceRectangle(Rectangle2D.Float rect) {
        if (userSpaceRectangle != null) {
            userSpaceRectangle = new Rectangle2D.Float(rect.x, rect.y,
                    rect.width, rect.height);
            getEntries().put(Annotation.RECTANGLE_KEY,
                    PRectangle.getPRectangleVector(userSpaceRectangle));
        }
    }

    /**
     * Gets the action to be performed when the annotation is activated.
     * For compatibility with the old org.icepdf.core.pobjects.Annotation.getAction()
     *
     * @return action to be activated, if no action, null is returned.
     */
    public org.icepdf.core.pobjects.actions.Action getAction() {
        Object tmp = library.getDictionary(entries, ACTION_KEY.getName());
        // initial parse will likely have the action as a dictionary, so we
        // create the new action object on the fly.  However it is also possible
        // that we are parsing an action that has no type specification and 
        // thus we can't use the parser to create the new action.
        if (tmp != null && tmp instanceof Hashtable) {
            Action action = Action.buildAction(library, (Hashtable) tmp);
            // assign reference if applicable
            if (action != null &&
                    library.isReference(entries, ACTION_KEY.getName())) {
                action.setPObjectReference(
                        library.getReference(entries, ACTION_KEY.getName()));
            }
            return action;
        }
        // subsequent new or edit actions will put in a reference and property
        // dictionary entry.
        tmp = getObject(ACTION_KEY);
        if (tmp != null && tmp instanceof Action) {
            return (Action) tmp;
        }
        return null;
    }

    /**
     * Adds the specified action to this annotation isnstance.  If the annotation
     * instance already has an action then this action replaces it.
     * <p/>
     * todo: future enhancment add support of next/muliple action chains.
     *
     * @param action action to add to this annotation.  This action must
     *               be created using the the ActionFactory in order to correctly setup
     *               the Pobject reference.
     * @return action that was added to Annotation, null if it was not success
     *         fully added.
     */
    public Action addAction(Action action) {

        // if no object ref we bail early.
        if (action.getPObjectReference() == null) {
            logger.severe("Addition of action was rejected null Object reference "
                    + action);
            return null;
        }

        // gen instance of state manager
        StateManager stateManager = library.getStateManager();

        // check if there is a 'dest' entry, if so we need to add this as a new
        // action, flag it for later processing.
        boolean isDestKey = getObject(LinkAnnotation.DESTINATION_KEY) != null;

        // check the annotation dictionary for an instance of an existing action
        if (getObject(ACTION_KEY) != null) {
            // if found we will add the new action at the beginning of the
            // next chain.
            boolean isReference = library.isReference(getEntries(),
                    ACTION_KEY.toString());
            // we have a next action that is an object, mark it for delete.
            // Because its a reference no need to flag the annotation as changed.
            if (isReference) {
                // mark this action for delete.
                Action oldAction = (Action) action.getObject(ACTION_KEY);
                oldAction.setDeleted(true);
                stateManager.addChange(new PObject(oldAction,
                        oldAction.getPObjectReference()));
            }
            // not a reference, we have an inline dictionary and we'll be
            // clearing it later, so we only need to add this annotation
            // to the state manager.
            else {
                getEntries().remove(ACTION_KEY);
                stateManager.addChange(new PObject(this, getPObjectReference()));
            }
        }
        // add the new action as per usual
        getEntries().put(ACTION_KEY, action.getPObjectReference());
        stateManager.addChange(new PObject(this, getPObjectReference()));

        // if this is a link annotation and there is a dest, we need to remove
        // as it is not allowed once an action has bee added.
        if (isDestKey && this instanceof LinkAnnotation) {
            // remove the dest key from the dictionary
            this.getEntries().remove(LinkAnnotation.DESTINATION_KEY);
            // mark the annotation as changed.
            stateManager.addChange(new PObject(this, getPObjectReference()));
        }

        // add the new action to the state manager.
        action.setNew(true);
        stateManager.addChange(new PObject(action, action.getPObjectReference()));
        // add it to the library so we can get it again. 
        library.addObject(action, action.getPObjectReference());

        return action;
    }

    /**
     * Deletes the annotation action specified as a paramater.  If an instance
     * of the specified action can not be found, no delete is make.
     *
     * @param action action to remove
     * @return true if the delete was successful, false otherwise.
     */
    public boolean deleteAction(Action action) {

        // gen instance of state manager
        StateManager stateManager = library.getStateManager();

        if (getObject(ACTION_KEY) != null) {
            // mark this action for delete.
            Action currentAction = getAction();
            if (currentAction.similar(action)) {
                // clear the action key for the annotation and add it as changed.
                // add the new action to the annotation
                getEntries().remove(ACTION_KEY);
                currentAction.setDeleted(true);
                // mark the action as changed.
                stateManager.addChange(new PObject(currentAction,
                        currentAction.getPObjectReference()));
                // mark the action as change.d 
                stateManager.addChange(new PObject(this, getPObjectReference()));
                return true;
            }
        }
        return false;
    }

    /**
     * Update the current annotation action with this entry.  This is very similar
     * to add but this method will return false if there was no previous annotation.
     * In such a case a call to addAction should be made.
     *
     * @param action action to update
     * @return true if the update was successful, othere; false.
     */
    public boolean updateAction(Action action) {
        // get instance of state manager
        StateManager stateManager = library.getStateManager();
        if (getObject(ACTION_KEY) != null) {
            Action currentAction = getAction();
            // check if we are updating an existing instance
            if (!currentAction.similar(action)) {
                stateManager.addChange(new PObject(action,
                        action.getPObjectReference()));
                currentAction.setDeleted(true);
                stateManager.addChange(new PObject(currentAction,
                        currentAction.getPObjectReference()));
            }
            // add the action to the annotation
            getEntries().put(ACTION_KEY, action.getPObjectReference());
            stateManager.addChange(new PObject(action,
                    action.getPObjectReference()));

            return true;
        }
        return false;
    }

    public boolean allowScreenNormalMode() {
        if (!allowScreenOrPrintRenderingOrInteraction())
            return false;
        return !getFlagNoView();
    }

    public boolean allowScreenRolloverMode() {
        if (!allowScreenOrPrintRenderingOrInteraction())
            return false;
        if (getFlagNoView() && !getFlagToggleNoView())
            return false;
        return !getFlagReadOnly();
    }

    public boolean allowScreenDownMode() {
        if (!allowScreenOrPrintRenderingOrInteraction())
            return false;
        if (getFlagNoView() && !getFlagToggleNoView())
            return false;
        return !getFlagReadOnly();
    }

    public boolean allowPrintNormalMode() {
        return allowScreenOrPrintRenderingOrInteraction() && getFlagPrint();
    }

    public boolean allowAlterProperties() {
        return !getFlagLocked();
    }

    public void setBorderStyle(BorderStyle borderStyle) {
        this.borderStyle = borderStyle;
        entries.put(Annotation.BORDER_STYLE_KEY, this.borderStyle);
    }

    public BorderStyle getBorderStyle() {
        return borderStyle;
    }

    public Vector<Number> getBorder() {
        return border;
    }

    public Annotation getParentAnnotation() {
        Annotation parent = null;

        Object ob = getObject("Parent");
        if (ob instanceof Reference)
            ob = library.getObject((Reference) ob);
        if (ob instanceof Annotation)
            parent = (Annotation) ob;
        else if (ob instanceof Hashtable)
            parent = Annotation.buildAnnotation(library, (Hashtable) ob);

        return parent;
    }

    public Page getPage() {
        Page page = (Page) getObject(PARENT_PAGE_KEY);
        if (page == null) {
            Annotation annot = getParentAnnotation();
            if (annot != null)
                page = annot.getPage();
        }
        return page;
    }

    /**
     * Gets the Link type,  can be either VISIBLE_RECTANGLE or
     * INVISIBLE_RECTANGLE, it all depends on the if the border or BS has
     * border width > 0.
     *
     * @return VISIBLE_RECTANGLE if the annotation has a visible borde, otherwise
     *         INVISIBLE_RECTANGLE
     */
    public int getLinkType() {
        // border style has W value for border with
        if (borderStyle != null) {
            if (borderStyle.getStrokeWidth() > 0) {
                return VISIBLE_RECTANGLE;
            }
        }
        // look for a border, 0,0,1 has one, 0,0,0 doesn't
        else if (border != null) {
            if (border.size() >= 3 && border.get(2).floatValue() > 0) {
                return VISIBLE_RECTANGLE;
            }
        }
        // should never happen
        return INVISIBLE_RECTANGLE;
    }

    /**
     * Gets the Annotation border style for the given annotation.  If no
     * annotation line style can be found the default value of BORDER_STYLE_SOLID
     * is returned.  Otherwise the bordStyle and border dictionaries are used
     * to deduse a line style.
     *
     * @return BorderSTyle line constants.
     */
    public String getLineStyle() {
        // check for border style
        if (borderStyle != null) {
            return borderStyle.getBorderStyle();
        }
        // check the border entry, will be solid or dashed
        else if (border != null) {
            if (border.size() > 3) {
                return BorderStyle.BORDER_STYLE_DASHED;
            } else if (border.get(2).floatValue() > 1) {
                return BorderStyle.BORDER_STYLE_SOLID;
            }
        }
        // default value
        return BorderStyle.BORDER_STYLE_SOLID;
    }

    /**
     * Gets the line thickness assoicated with this annotation.
     *
     * @return point value used when drawing line thickness.
     */
    public float getLineThickness() {
        // check for border style
        if (borderStyle != null) {
            return borderStyle.getStrokeWidth();
        }
        // check the border entry, will be solid or dashed
        else if (border != null) {
            if (border.size() >= 3) {
                return border.get(2).floatValue();
            }
        }
        return 0;
    }

    /**
     * Checks to see if the annotation has defined a drawable border width.
     *
     * @return true if a border will be drawn; otherwise, false.
     */
    public boolean isBorder() {
        boolean borderWidth = false;
        Object border = getObject(BORDER_KEY);
        if (border != null && border instanceof Vector) {
            Vector borderProps = (Vector) border;
            if (borderProps.size() == 3) {
                borderWidth = ((Number) borderProps.get(2)).floatValue() > 0;
            }
        }
        return getBorderStyle() != null || borderWidth;
    }

    public void render(Graphics2D origG, int renderHintType,
                       float totalRotation, float userZoom,
                       boolean tabSelected) {
        if (!allowScreenOrPrintRenderingOrInteraction())
            return;
        if (renderHintType == GraphicsRenderingHints.SCREEN && !allowScreenNormalMode())
            return;
        if (renderHintType == GraphicsRenderingHints.PRINT && !allowPrintNormalMode())
            return;

//System.out.println("render(-)  " + this);
        Rectangle2D.Float rect = getUserSpaceRectangle();
// Show original ractangle, without taking into consideration NoZoom and NoRotate
//System.out.println("Original rectangle: " + rect);
//origG.setColor( Color.blue );
//origG.draw( rect );
//origG.setColor( Color.red );
//Line2D.Double topLine = new Line2D.Double( rect.getMinX(), rect.getMaxY(), rect.getMaxX(), rect.getMaxY() );
//origG.draw( topLine );
//origG.setColor( Color.yellow );
//Line2D.Double bottomLine = new Line2D.Double( rect.getMinX(), rect.getMinY(), rect.getMaxX(), rect.getMinY() );
//origG.draw( bottomLine );

        AffineTransform oldAT = origG.getTransform();
        Shape oldClip = origG.getClip();

        // Simply uncomment the //// lines to use a different Graphics object
        Graphics2D g = origG;
        ////Graphics2D g = (Graphics2D) origG.create();

        AffineTransform at = new AffineTransform(oldAT);
        at.translate(rect.getMinX(), rect.getMinY());

        boolean noRotate = getFlagNoRotate();
        if (noRotate) {
            float unRotation = -totalRotation;
            while (unRotation < 0.0f)
                unRotation += 360.0f;
            while (unRotation > 360.0f)
                unRotation -= 360.0f;
            if (unRotation == -0.0f)
                unRotation = 0.0f;

            if (unRotation != 0.0) {
                double radians = Math.toRadians(unRotation); // unRotation * Math.PI / 180.0
                AffineTransform rotationTransform =
                        AffineTransform.getRotateInstance(radians);
                Point2D.Double origTopLeftCorner = new Point2D.Double(0.0, Math.abs(rect.getHeight()));
                Point2D rotatedTopLeftCorner = rotationTransform.transform(origTopLeftCorner, null);
                at.translate(origTopLeftCorner.getX() - rotatedTopLeftCorner.getX(),
                        origTopLeftCorner.getY() - rotatedTopLeftCorner.getY());
                at.rotate(radians);
            }
        }

        boolean noZoom = getFlagNoZoom();
        if (noZoom) {
            double scaleY = Math.abs(at.getScaleY());
            if (scaleY != 1.0) {
                double scaleX = Math.abs(at.getScaleX());
                double rectHeight = Math.abs(rect.getHeight());
                double resizedY = rectHeight * ((scaleY - 1.0) / scaleY);
                at.translate(0.0, resizedY);
                at.scale(1.0 / scaleX, 1.0 / scaleY);
            }
        }

        GraphicsRenderingHints grh = GraphicsRenderingHints.getDefault();
        g.setRenderingHints(grh.getRenderingHints(renderHintType));
        g.setTransform(at);
        Shape preAppearanceStreamClip = g.getClip();
        g.clip(deriveDrawingRectangle());

        renderAppearanceStream(g);

        g.setTransform(at);
        g.setClip(preAppearanceStreamClip);

        if (tabSelected) {
            renderBorderTabSelected(g);
        } else {
            renderBorder(g);
        }

        g.setTransform(oldAT);
        g.setClip(oldClip);

        ////g.dispose();

// Show the top left corner, that NoZoom and NoRotate annotations cling to
//origG.setColor( Color.blue );
//Rectangle2D.Double topLeft = new Rectangle2D.Double(
//    rect.getMinX(), rect.getMaxY()-3, 3, 3 );
//origG.fill( topLeft );
    }

    // TODO add support for rollover and down states..
    protected void renderAppearanceStream(Graphics2D g) {
        Object AP = getObject(APPEARANCE_STREAM_KEY);
        if (AP instanceof Hashtable) {
            Object N = library.getObject(
                    (Hashtable) AP, APPEARANCE_STREAM_NORMAL_KEY.getName());
            if (N instanceof Hashtable) {
                Object AS = getObject(APPEARANCE_STATE_KEY);
                if (AS != null)
                    N = library.getObject((Hashtable) N, AS.toString());
            }

            Shapes shapes = null;
            AffineTransform matrix = null;
            Rectangle2D bbox = null;
            // n should be a Form but we have a few cases of Stream
            if (N instanceof Form) {
                Form form = (Form) N;
                form.init();
                shapes = form.getShapes();
                matrix = form.getMatrix();
                bbox = form.getBBox();
            } else if (N instanceof Stream) {

                Stream stream = (Stream) N;
                Resources res = library.getResources(stream.getEntries(), "Resources");
                bbox = library.getRectangle(stream.getEntries(), "BBox");
                matrix = new AffineTransform();
                InputStream sis = stream.getInputStreamForDecodedStreamBytes();
                try {
                    ContentParser cp = new ContentParser(library, res);
                    shapes = cp.parse(sis);
                } catch (Exception e) {
                    shapes = new Shapes();
                    logger.log(Level.FINE, "Error initializing Page.", e);
                } finally {
                    try {
                        sis.close();
                    } catch (IOException e) {
                        logger.log(Level.FINE, "Error closing page stream.", e);
                    }
                }
            }
            if (shapes != null) {
//g.setColor( Color.blue );
//Rectangle2D.Double newRect = deriveDrawingRectangle();
//g.draw( newRect );


                // step 1. appearance bounding box (BBox) is transformed, using
                // Matrix, to produce a quadrilateral with arbitrary orientation.
                Rectangle2D tBbox = matrix.createTransformedShape(bbox).getBounds2D();

                // Step 2. matrix a is computed that scales and translates the
                // transformed appearance box (tBbox) to align with the edges of
                // the annotation's rectangle (Ret).
                Rectangle2D rect = getUserSpaceRectangle();
                AffineTransform tAs = AffineTransform.getScaleInstance(
                        (rect.getWidth() / tBbox.getWidth()),
                        (rect.getHeight() / tBbox.getHeight()));
                // something not quite right here.
//                AffineTransform tAt = AffineTransform.getTranslateInstance(
//                        (tBbox.getX() - rect.getX()),
//                        (tBbox.getY() - rect.getY()));
//                tAs.concatenate(tAt);
                // Step 3. matrix is concatenated with A to form a matrix AA
                // that maps from the appearance's coordinate system to the
                // annotation's rectangle in default user space.
                tAs.concatenate(matrix);
                g.transform(tAs);

//System.out.println("Form: " + form.getEntries());
//String str = new String( form.getBytes() );
//System.out.println( str );
//System.out.println("Shapes: " + shapes + "  count: " + shapes.getShapesCount());
                // check to see if we are painting highlight annotations.
                // if so we add some transparency to the context.
                boolean isTransparency = Defs.sysPropertyBoolean("org.icepdf.core.paint.disableAlpha");
                if (subtype != null && SUBTYPE_HIGHLIGHT.equals(subtype)) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .30f));
                    // remove other alpha defs from parsing
                    Defs.setSystemProperty("org.icepdf.core.paint.disableAlpha", "true");
                }
                // regular paint
                shapes.paint(g);
                // switch transparency back to the default value
                if (subtype != null && SUBTYPE_HIGHLIGHT.equals(subtype)) {
                    // remove other alpha defs from parsing
                    Defs.setSystemProperty("org.icepdf.core.paint.disableAlpha",
                            String.valueOf(isTransparency));
                }
            }
        }
    }

    protected void renderBorder(Graphics2D g) {
//        if( false ) {
//            float width = 1.0f;
//            Rectangle2D.Float jrect = deriveBorderDrawingRectangle( width );
//            g.setColor( Color.red );
//            g.setStroke( new BasicStroke(width) );
//            g.draw( jrect );
//            return;
//        }

        Color borderColor = getColor();
        if (borderColor != null) {
            g.setColor(borderColor);
        }

        BorderStyle bs = getBorderStyle();
        if (bs != null) {
            float width = bs.getStrokeWidth();
            if (width > 0.0f && borderColor != null && canDrawBorder) {
                Rectangle2D.Float jrect = deriveBorderDrawingRectangle(width);

                if (bs.isStyleSolid()) {
                    g.setStroke(new BasicStroke(width));
                    g.draw(jrect);
                } else if (bs.isStyleDashed()) {
                    BasicStroke stroke = new BasicStroke(
                            width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                            10.0f, bs.getDashArray(), 0.0f);
                    g.setStroke(stroke);
                    g.draw(jrect);
                } else if (bs.isStyleBeveled()) {
                    jrect = deriveDrawingRectangle();

                    g.setStroke(new BasicStroke(1.0f));
                    Line2D.Double line;

                    // Upper top
                    g.setColor(BorderStyle.LIGHT);
                    line = new Line2D.Double( // Top line
                            jrect.getMinX() + 1.0, jrect.getMaxY() - 1.0, jrect.getMaxX() - 2.0, jrect.getMaxY() - 1.0);
                    g.draw(line);
                    line = new Line2D.Double( // Left line
                            jrect.getMinX() + 1.0f, jrect.getMinY() + 2.0, jrect.getMinX() + 1.0f, jrect.getMaxY() - 1.0);
                    g.draw(line);

                    // Inner top
                    g.setColor(BorderStyle.LIGHTEST);
                    line = new Line2D.Double( // Top line
                            jrect.getMinX() + 2.0, jrect.getMaxY() - 2.0, jrect.getMaxX() - 3.0, jrect.getMaxY() - 2.0);
                    g.draw(line);
                    line = new Line2D.Double( // Left line
                            jrect.getMinX() + 2.0f, jrect.getMinY() + 3.0, jrect.getMinX() + 2.0f, jrect.getMaxY() - 2.0);
                    g.draw(line);

                    // Inner bottom
                    g.setColor(BorderStyle.DARK);
                    line = new Line2D.Double( // Bottom line
                            jrect.getMinX() + 2.0, jrect.getMinY() + 2.0, jrect.getMaxX() - 2.0, jrect.getMinY() + 2.0);
                    g.draw(line);
                    line = new Line2D.Double( // Right line
                            jrect.getMaxX() - 2.0f, jrect.getMinY() + 2.0, jrect.getMaxX() - 2.0f, jrect.getMaxY() - 2.0);
                    g.draw(line);

                    // Lower bottom
                    g.setColor(BorderStyle.DARKEST);
                    line = new Line2D.Double( // Bottom line
                            jrect.getMinX() + 1.0, jrect.getMinY() + 1.0, jrect.getMaxX() - 1.0, jrect.getMinY() + 1.0);
                    g.draw(line);
                    line = new Line2D.Double( // Right line
                            jrect.getMaxX() - 1.0f, jrect.getMinY() + 1.0, jrect.getMaxX() - 1.0f, jrect.getMaxY() - 1.0);
                    g.draw(line);
                } else if (bs.isStyleInset()) {
                    jrect = deriveDrawingRectangle();

                    g.setStroke(new BasicStroke(1.0f));
                    Line2D.Double line;

                    // Upper top
                    g.setColor(BorderStyle.DARK);
                    line = new Line2D.Double( // Top line
                            jrect.getMinX() + 1.0, jrect.getMaxY() - 1.0, jrect.getMaxX() - 1.0, jrect.getMaxY() - 1.0);
                    g.draw(line);
                    line = new Line2D.Double( // Left line
                            jrect.getMinX() + 1.0f, jrect.getMinY() + 1.0, jrect.getMinX() + 1.0f, jrect.getMaxY() - 1.0);
                    g.draw(line);

                    // Inner top
                    g.setColor(BorderStyle.DARKEST);
                    line = new Line2D.Double( // Top line
                            jrect.getMinX() + 2.0, jrect.getMaxY() - 2.0, jrect.getMaxX() - 2.0, jrect.getMaxY() - 2.0);
                    g.draw(line);
                    line = new Line2D.Double( // Left line
                            jrect.getMinX() + 2.0f, jrect.getMinY() + 2.0, jrect.getMinX() + 2.0f, jrect.getMaxY() - 2.0);
                    g.draw(line);

                    // Inner bottom
                    g.setColor(BorderStyle.LIGHTEST);
                    line = new Line2D.Double( // Bottom line
                            jrect.getMinX() + 3.0, jrect.getMinY() + 2.0, jrect.getMaxX() - 2.0, jrect.getMinY() + 2.0);
                    g.draw(line);
                    line = new Line2D.Double( // Right line
                            jrect.getMaxX() - 2.0f, jrect.getMinY() + 2.0, jrect.getMaxX() - 2.0f, jrect.getMaxY() - 3.0);
                    g.draw(line);

                    // Lower bottom
                    g.setColor(BorderStyle.LIGHT);
                    line = new Line2D.Double( // Bottom line
                            jrect.getMinX() + 2.0, jrect.getMinY() + 1.0, jrect.getMaxX() - 1.0, jrect.getMinY() + 1.0);
                    g.draw(line);
                    line = new Line2D.Double( // Right line
                            jrect.getMaxX() - 1.0f, jrect.getMinY() + 1.0, jrect.getMaxX() - 1.0f, jrect.getMaxY() - 2.0);
                    g.draw(line);
                } else if (bs.isStyleUnderline()) {
                    g.setStroke(new BasicStroke(width));
                    Line2D.Double line = new Line2D.Double(
                            jrect.getMinX(), jrect.getMinY(), jrect.getMaxX(), jrect.getMinY());
                    g.draw(line);
                }
            }
        } else {
            Vector borderVector = (Vector) getObject(BORDER_KEY);
            if (borderVector != null) {
                if (borderColor != null) {
                    float horizRadius = 0.0f;
                    float vertRadius = 0.0f;
                    float width = 1.0f;
                    float[] dashArray = null;
                    if (borderVector.size() >= 1)
                        horizRadius = ((Number) borderVector.get(0)).floatValue();
                    if (borderVector.size() >= 2)
                        vertRadius = ((Number) borderVector.get(1)).floatValue();
                    if (borderVector.size() >= 3)
                        width = ((Number) borderVector.get(2)).floatValue();
                    if (borderVector.size() >= 4) {
                        Object dashObj = borderVector.get(3);
                        // I guess some encoders like having fun with us,
                        //  and feed a number when a number-array is appropriate. The problem
                        //  is that for the specific PDF given, apparently no border is to be
                        //  drawn, especially not the hugely thinck one described.  So,
                        //  instead of interpretting the 4th element (Number) into a Vector,
                        //  I'm just not going to do the border if it's the Number.  I know, hack.
                        // The only theory I have is that LinkAnnotation defaults the border
                        //  color to black, when maybe it should be to null, but that could
                        //  change a _lot_ of stuff, so I won't touch it now.
                        if (dashObj instanceof Number) {
                            // Disable border drawing
                            width = 0.0f;
                        } else if (dashObj instanceof Vector) {
                            Vector dashVector = (Vector) borderVector.get(3);
                            int sz = dashVector.size();
                            dashArray = new float[sz];
                            for (int i = 0; i < sz; i++) {
                                Number num = (Number) dashVector.get(i);
                                dashArray[i] = num.floatValue();
                            }
                        }
                    }

                    if (width > 0.0f) {
                        Rectangle2D.Float jrect = deriveBorderDrawingRectangle(width);
                        RoundRectangle2D.Double roundRect = new RoundRectangle2D.Double(
                                jrect.getX(), jrect.getY(), jrect.getWidth(), jrect.getHeight(),
                                horizRadius, vertRadius);
                        BasicStroke stroke = new BasicStroke(
                                width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                                10.0f, dashArray, 0.0f);
                        g.setStroke(stroke);
                        g.draw(roundRect);
                    }
                }
            } else {
                // Draw a solid rectangle, 1 pixel wide
                if (borderColor != null && SUBTYPE_LINK.equals(subtype)) {
                    float width = 1.0f;
                    Rectangle2D.Float jrect = deriveBorderDrawingRectangle(width);
                    g.setStroke(new BasicStroke(width));
                    g.draw(jrect);
                }
            }
        }
    }

    protected void renderBorderTabSelected(Graphics2D g) {
        float width = 1.0f;
        Rectangle2D.Float jrect = deriveBorderDrawingRectangle(width);
        g.setColor(Color.black);
        float[] dashArray = new float[]{2.0f};
        BasicStroke stroke = new BasicStroke(
                width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10.0f, dashArray, 0.0f);
        g.setStroke(stroke);
        g.draw(jrect);
    }

    /**
     * Gest the RGB colour of the annotation used for the following purposes:
     * <ul>
     * <li>the background of the annotaiton's icon when closed</li>
     * <li>the title bar of the anntoation's pop-up window</li>
     * <li>the border of a link annotation</li>
     * </ul>
     *
     * @return A Color for the border, or null if none is to be used
     */
    public Color getColor() {
        return color;
    }

    /**
     * Sets the Annotation colour and underlying
     *
     * @param color
     */
    public void setColor(Color color) {
        this.color = new Color(color.getRGB());
        // put colour back in to the dictionary
        float[] compArray = new float[3];
        this.color.getColorComponents(compArray);
        Vector<Number> colorValues = new Vector<Number>(3);
        colorValues.add(compArray[0]);
        colorValues.add(compArray[1]);
        colorValues.add(compArray[2]);
        entries.put(Annotation.COLOR_KEY, colorValues);
    }

    private Rectangle2D.Float deriveDrawingRectangle() {
        Rectangle2D.Float origRect = getUserSpaceRectangle();
        Rectangle2D.Float jrect = new Rectangle2D.Float(origRect.x, origRect.y,
                origRect.width, origRect.height);
        jrect.x = 0.0f;
        jrect.y = 0.0f;
        return jrect;
    }

    private Rectangle2D.Float deriveBorderDrawingRectangle(float borderWidth) {
        Rectangle2D.Float jrect = deriveDrawingRectangle();

        float halfBorderWidth = borderWidth / 2.0f;
        double minX = jrect.getMinX() + halfBorderWidth;
        double minY = jrect.getMinY() + halfBorderWidth;
        double maxX = jrect.getMaxX() - halfBorderWidth;
        double maxY = jrect.getMaxY() - halfBorderWidth;
        jrect.setFrameFromDiagonal(minX, minY, maxX, maxY);
        return jrect;
    }

    /**
     * @return Whether this annotation may be shown in any way to the user
     */
    protected boolean allowScreenOrPrintRenderingOrInteraction() {
        // Based off of the annotation flags' Invisible and Hidden values
        if (getFlagHidden())
            return false;
        if (getFlagInvisible() && isSupportedAnnotationType())
            return false;
        return true;
    }

    /**
     * The PDF spec defines rules for displaying annotation subtypes that the viewer
     * does not recognise. But, from a product point of view, we may or may not
     * wish to make a best attempt at showing an unsupported annotation subtype,
     * as that may make users think we're quality deficient, instead of
     * feature deficient.
     * Subclasses should override this, and return true, indicating that that
     * particular annotation is supported.
     *
     * @return true, if this annotation is supported; else, false.
     */
    protected boolean isSupportedAnnotationType() {
        return true;
    }

    public boolean getFlagInvisible() {
        return ((getInt(FLAG_KEY.getName()) & 0x0001) != 0);
    }

    public boolean getFlagHidden() {
        return ((getInt(FLAG_KEY.getName()) & 0x0002) != 0);
    }

    public boolean getFlagPrint() {
        return ((getInt(FLAG_KEY.getName()) & 0x0004) != 0);
    }

    public boolean getFlagNoZoom() {
        return ((getInt(FLAG_KEY.getName()) & 0x0008) != 0);
    }

    public boolean getFlagNoRotate() {
        return ((getInt(FLAG_KEY.getName()) & 0x0010) != 0);
    }

    public boolean getFlagNoView() {
        return ((getInt(FLAG_KEY.getName()) & 0x0020) != 0);
    }

    public boolean getFlagReadOnly() {
        return ((getInt(FLAG_KEY.getName()) & 0x0040) != 0);
    }

    /**
     * A locked annotation can not be deleted or its properties  such
     * as position and size to be modified by the user. This property does not
     * restrict annotation contents such as the value of a form field.
     *
     * @return true if locked, false otherwise.
     */
    public boolean getFlagLocked() {
        return ((getInt(FLAG_KEY.getName()) & 0x0080) != 0);
    }

    public boolean getFlagToggleNoView() {
        return ((getInt(FLAG_KEY.getName()) & 0x0100) != 0);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ANNOTATION= {");
        java.util.Enumeration keys = entries.keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = entries.get(key);
            sb.append(key.toString());
            sb.append('=');
            if (value == null)
                sb.append("null");
            else if (value instanceof StringObject)
                sb.append(((StringObject) value).getDecryptedLiteralString(library.securityManager));
            else
                sb.append(value.toString());
            sb.append(',');
        }
        sb.append('}');
        if (getPObjectReference() != null) {
            sb.append("  ");
            sb.append(getPObjectReference());
        }
        for (int i = sb.length() - 1; i >= 0; i--) {
            if (sb.charAt(i) < 32 || sb.charAt(i) >= 127)
                sb.deleteCharAt(i);
        }
        return sb.toString();
    }
}
