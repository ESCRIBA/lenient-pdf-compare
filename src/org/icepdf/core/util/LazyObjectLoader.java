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
package org.icepdf.core.util;

import org.icepdf.core.io.SeekableInput;
import org.icepdf.core.pobjects.*;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Mark Collette
 * @since 2.0
 */
public class LazyObjectLoader implements MemoryManagerDelegate {

    private static final Logger logger =
            Logger.getLogger(LazyObjectLoader.class.toString());

    private Library library;
    private SeekableInput m_SeekableInput;
    private CrossReference m_CrossReference;
    protected ArrayList<ObjectStream> leastRecentlyUsed; // ArrayList<ObjectStream>
    private final Object leastRectlyUsedLock = new Object();

    public LazyObjectLoader(Library lib, SeekableInput seekableInput, CrossReference xref) {
        library = lib;
        m_SeekableInput = seekableInput;
        m_CrossReference = xref;
        leastRecentlyUsed = new ArrayList<ObjectStream>(256);
    }

    public boolean loadObject(Reference reference) {
        if (reference == null || library == null || m_CrossReference == null)
            return false;
        int objNum = reference.getObjectNumber();
        CrossReference.Entry entry = m_CrossReference.getEntryForObject(objNum);
        if (entry == null)
            return false;
        boolean gotSomething = false;
        if (entry instanceof CrossReference.UsedEntry) {
            try {
                if (m_SeekableInput != null) {
                    m_SeekableInput.beginThreadAccess();
                    CrossReference.UsedEntry usedEntry = (CrossReference.UsedEntry) entry;
                    long position = usedEntry.getFilePositionOfObject();
                    long savedPosition = m_SeekableInput.getAbsolutePosition();
                    m_SeekableInput.seekAbsolute(position);
                    Parser parser = new Parser(m_SeekableInput);
                    Object ob = parser.getObject(library);
                    gotSomething = (ob != null);
                    m_SeekableInput.seekAbsolute(savedPosition);
                }
            }
            catch (Exception e) {
                logger.log(Level.SEVERE,
                        "Error loading object instance: " + reference.toString(), e);
            }
            finally {
                if (m_SeekableInput != null)
                    m_SeekableInput.endThreadAccess();
            }
        } else if (entry instanceof CrossReference.CompressedEntry) {
            try {
                CrossReference.CompressedEntry compressedEntry = (CrossReference.CompressedEntry) entry;
                int objectStreamsObjectNumber = compressedEntry.getObjectNumberOfContainingObjectStream();
                int objectIndex = compressedEntry.getIndexWithinObjectStream();
                Reference objectStreamRef = new Reference(objectStreamsObjectNumber, 0);
                ObjectStream objectStream = (ObjectStream) library.getObject(objectStreamRef);
                if (objectStream != null) {
                    synchronized (leastRectlyUsedLock) {
                        leastRecentlyUsed.remove(objectStream);
                        leastRecentlyUsed.add(objectStream);
                    }

                    gotSomething = objectStream.loadObject(library, objectIndex);
                }
            }
            catch (Exception e) {
               logger.log(Level.SEVERE,
                       "Error loading object instance: " + reference.toString(), e);
            }
        }
        return gotSomething;
    }

    public boolean haveEntry(Reference reference) {
        if (reference == null || m_CrossReference == null)
            return false;
        int objNum = reference.getObjectNumber();
        CrossReference.Entry entry = m_CrossReference.getEntryForObject(objNum);
        return (entry != null);
    }

    public PTrailer loadTrailer(long position) {
        PTrailer trailer = null;
        try {
            if (m_SeekableInput != null) {
                m_SeekableInput.beginThreadAccess();
                long savedPosition = m_SeekableInput.getAbsolutePosition();
                m_SeekableInput.seekAbsolute(position);
                Parser parser = new Parser(m_SeekableInput);
                Object obj = parser.getObject(library);
                if (obj instanceof PObject)
                    obj = ((PObject) obj).getObject();
                trailer = (PTrailer) obj;
                if (trailer != null)
                    trailer.setPosition(position);
                m_SeekableInput.seekAbsolute(savedPosition);
            }
        }
        catch (Exception e) {
            logger.log(Level.FINE,
                    "Error loading PTrailer instance: " + position, e);
        }
        finally {
            if (m_SeekableInput != null)
                m_SeekableInput.endThreadAccess();
        }
        return trailer;
    }

    public void dispose() {
        library = null;
        m_SeekableInput = null;
        m_CrossReference = null;
        if (leastRecentlyUsed != null) {
            leastRecentlyUsed.clear();
            leastRecentlyUsed = null;
        }
    }


    //
    // MemoryManagerDelegate interface
    //

    public boolean reduceMemory(int reductionPolicy) {
        int numToDo = 0;
        synchronized (leastRectlyUsedLock) {
            int lruSize = leastRecentlyUsed.size();
            if (reductionPolicy == MemoryManagerDelegate.REDUCE_AGGRESSIVELY) {
                numToDo = lruSize * 75 / 100;
            } else if (reductionPolicy == MemoryManagerDelegate.REDUCE_SOMEWHAT) {
                if (lruSize > 5)
                    numToDo = lruSize * 50 / 100;
                else if (lruSize > 0)
                    numToDo = 1;
            }
//System.out.println("LazyObjectLoader.reduceMemory()  reductionPolicy: " + reductionPolicy + ",  numToDo: " + numToDo + ",  lruSize: " + lruSize);
            for (int i = 0; i < numToDo; i++) {
                ObjectStream objStm = leastRecentlyUsed.remove(0);
                if (objStm != null)
                    objStm.dispose(true);
            }
        }
        return numToDo > 0;
    }

    /**
     * Get the documents library object.
     *
     * @return documents library object.
     */
    public Library getLibrary() {
        return library;
    }
}
