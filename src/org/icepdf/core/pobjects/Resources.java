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
package org.icepdf.core.pobjects;

import org.icepdf.core.pobjects.graphics.*;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A resource is a dictionary type as defined by the PDF specification.  It
 * can contain fonts, xobjects, colorspaces, patterns, shading and
 * external graphic states.
 *
 * @since 1.0
 */
public class Resources extends Dictionary {

    // shared resource counter. 
    private static int uniqueCounter = 0;

    private static synchronized int getUniqueId() {
        return uniqueCounter++;
    }

    private static final Logger logger =
            Logger.getLogger(Resources.class.toString());

    Hashtable fonts;
    Hashtable xobjects;
    Hashtable colorspaces;
    Hashtable patterns;
    Hashtable shading;
    Hashtable extGStates;

    // reference count to keep track of how many objects reference this resource.
    private int referenceCount;

    /**
     * @param l
     * @param h
     */
    public Resources(Library l, Hashtable h) {
        super(l, h);
        colorspaces = library.getDictionary(entries, "ColorSpace");
        fonts = library.getDictionary(entries, "Font");
        xobjects = library.getDictionary(entries, "XObject");
        patterns = library.getDictionary(entries, "Pattern");
        shading = library.getDictionary(entries, "Shading");
        extGStates = library.getDictionary(entries, "ExtGState");
        referenceCount = 0;
    }

    /**
     * Increments the refernce count, meaning that at least one object is
     * depending on this reference. 
     *
     * @param referer object doing the reference, used for debug purposes.
     */
    public void addReference(Object referer) {
        synchronized(this) {
            referenceCount++;
            ////System.out.println("Resources.addReference()  " + getPObjectReference() + "  " + uniqueId + "  count: " + referenceCount);
            ////System.out.println("Resources.addReference()    referer: " + referer.getPObjectReference() + "  " + referer.getClass().getSimpleName());
            //if (referer instanceof Page) {
            //    Thread.dumpStack();
            //}
        }
    }

    public void removeReference(Object referer) {
        synchronized(this) {
            referenceCount--;
            ////System.out.println("Resources.removeReference()  " + getPObjectReference() + "  " + uniqueId + "  count: " + referenceCount);
            ////System.out.println("Resources.removeReference()    referer: " + referer.getPObjectReference() + "  " + referer.getClass().getSimpleName());
        }
    }

    /**
     * Disposes this classes resources if an only if no other PObject
     * is also using this oject.  If no other PObject references this instance
     * then we can dispose of image, stream and xform objects.
     *
     * @param cache true to cache image streams, false otherwise.
     * @param referer only used for debuggin, can be null otherwise.
     */
    public boolean  dispose(boolean cache, Dictionary referer) {
        synchronized(this) {
            referenceCount--;
            ////System.out.println("Resources.dispose()  " + getPObjectReference() + "  " + uniqueId + "  count: " + referenceCount + "  cache: " + cache);
            ////System.out.println("Resources.dispose()    referer: " + referer.getPObjectReference() + "  " + referer.getClass().getSimpleName());
            // we have a reference so we can't dispose.
            if (referenceCount > 0) {
                ////System.out.println("Resources.dispose()      REDUNDANT");
                return false;
            }
        }

        // NOTE: Make sure not to clear fonts, color spaces, pattern,
        // or extGStat's as this hold reverences to object not the actual
        // object. The only images contain object with a lot of memory

        if (xobjects != null) {
            Enumeration xobjectContent = xobjects.elements();
            while (xobjectContent.hasMoreElements()) {
                Object tmp = xobjectContent.nextElement();
                if (tmp instanceof Stream) {
                    Stream stream = (Stream) tmp;
                    stream.dispose(cache);
                }
                if (tmp instanceof Reference) {
                    Object reference = library.getObject((Reference) tmp);
                    if (reference instanceof Form) {
                        Form form = (Form) reference;
                        form.dispose(cache);
                    }
                    if (reference instanceof Stream) {
                        Stream stream = (Stream) reference;
                        stream.dispose(cache);
                    }
                }
            }
        }
        // remove refernces from library
        clearResource(colorspaces);
        clearResource(fonts);
        clearResource(xobjects);
        clearResource(patterns);
        clearResource(shading);
        clearResource(extGStates);
        return true;
    }

    private void clearResource(Hashtable resource){
        if (resource != null){
            Set keys = resource.keySet();
            Object value;
            for (Object key : keys){
                value = resource.get(key);
                if (value instanceof Reference){
                    library.removeObject((Reference)value);
                }
            }
        }
    }

    /**
     * @param o
     * @return
     */
    public PColorSpace getColorSpace(Object o) {

        if (o == null) {
            return null;
        }

        Object tmp;
        // every resource has a color space entry and o can be tmp in it.
        if (colorspaces != null && colorspaces.get(o) != null) {
            tmp = colorspaces.get(o);
            PColorSpace cs = PColorSpace.getColorSpace(library, tmp);
            if (cs != null) {
                cs.init();
            }
            return cs;
        }
        // look for our name in the pattern dictionary
        if (patterns != null && patterns.get(o) != null) {
            tmp = patterns.get(o);
            PColorSpace cs = PColorSpace.getColorSpace(library, tmp);
            if (cs != null) {
                cs.init();
            }
            return cs;
        }

        // if its not in color spaces or pattern then its a plain old
        // named colour space.  
        PColorSpace cs = PColorSpace.getColorSpace(library, o);
        if (cs != null) {
            cs.init();
        }
        return cs;

    }

    /**
     * @param s
     * @return
     */
    public org.icepdf.core.pobjects.fonts.Font getFont(String s) {
        org.icepdf.core.pobjects.fonts.Font font = null;
        if (fonts != null) {
            Object ob = fonts.get(s);
            // check to make sure the library contains a font
            if (ob instanceof org.icepdf.core.pobjects.fonts.Font) {
                font = (org.icepdf.core.pobjects.fonts.Font) ob;
            }
            // the default value is most likely Reference
            else if (ob instanceof Reference) {
                font = (org.icepdf.core.pobjects.fonts.Font) library.getObject((Reference) ob);
            }
        }
        if (font != null) {
            font.init();
        }
        return font;
    }

    /**
     * @param s
     * @param fill
     * @return
     */
    public Image getImage(String s, Color fill) {

        // check xobjects for stream
        Stream st = (Stream) library.getObject(xobjects, s);
        if (st == null) {
            return null;
        }
        // return null if the xobject is not an image
        if (!st.isImageSubtype()) {
            return null;
        }
        // lastly return the images.
        Image image = null;
        try {
            image = st.getImage(fill, this, true);
            // clean up the stream's resources.
            st.dispose(true);
        }
        catch (Exception e) {
            logger.log(Level.FINE, "Error getting image by name: " + s, e);
        }
        return image;
    }

    /**
     * @param s
     * @return
     */
    public boolean isForm(String s) {
        Object o = library.getObject(xobjects, s);
        return o instanceof Form;
    }

    /**
     * Gets the Form XObject specified by the named reference.
     *
     * @param nameReference name of resourse to retreive.
     * @return if the named reference is found return it, otherwise return null;
     */
    public Form getForm(String nameReference) {
        Form formXObject = null;
        Object tempForm = library.getObject(xobjects, nameReference);
        if (tempForm instanceof Form) {
            formXObject = (Form) tempForm;
        }
        return formXObject;
    }

    /**
     * Retrieves a Pattern object given the named resource.  This can be
     * call for a fill, text fill or do image mask.
     *
     * @param name of object to find.
     * @return tiling or shading type pattern object.  If not constructor is
     *         found, then null is returned.
     */
    public Pattern getPattern(String name) {
        if (patterns != null) {

            Object attribute = library.getObject(patterns, name);
            // An instance of TilingPattern will always have a stream
            if (attribute != null && attribute instanceof TilingPattern) {
                return (TilingPattern) attribute;
            }
            else  if (attribute != null && attribute instanceof Stream) {
                return new TilingPattern((Stream)attribute);
            }
            // ShaddingPatterns will not have a stream but still need to parsed
            else if (attribute != null && attribute instanceof Hashtable) {
                return ShadingPattern.getShadingPattern(library,
                        (Hashtable) attribute);
            }
        }
        return null;
    }

    /**
     * Gets the shadding pattern based on a shading dictionary name,  similar
     * to getPattern but is only called for the 'sh' token.
     *
     * @param name name of shading dictionary
     * @return associated shading pattern if any.
     */
    public ShadingPattern getShading(String name) {
        // look for pattern name in the shading dictionary, used by 'sh' tokens
        if (shading != null) {
            Object shadingDictionary = library.getObject(shading, name);
            if (shadingDictionary != null && shadingDictionary instanceof Hashtable) {
                return ShadingPattern.getShadingPattern(library, entries,
                        (Hashtable) shadingDictionary);
            }
//            else if (shadingDictionary != null && shadingDictionary instanceof Stream) {
//                System.out.println("Found Type 6 shading pattern.... returning empty pattern data. ");
            // todo: alter parser to take into account stream shading types...
//                return new ShadingType6Pattern(library, null);
//                return null;
//            }
        }
        return null;
    }

    /**
     * Returns the ExtGState object which has the specified reference name.
     *
     * @param namedReference name of ExtGState object to try and find.
     * @return ExtGState which contains the named references ExtGState attrbutes,
     *         if the namedReference could not be found null is returned.
     */
    public ExtGState getExtGState(String namedReference) {
        ExtGState gsState = null;
        if (extGStates != null) {
            Object attribute = library.getObject(extGStates, namedReference);
            if (attribute instanceof Hashtable) {
                gsState = new ExtGState(library, (Hashtable) attribute);
            } else if (attribute instanceof Reference) {
                gsState = new ExtGState(library,
                        (Hashtable) library.getObject(
                                (Reference) attribute));
            }
        }
        return gsState;

    }
}
