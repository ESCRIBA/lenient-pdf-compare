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
package org.icepdf.core.io;

import java.io.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * @author Mark Collette
 * @since 2.0
 */
public class RandomAccessFileInputStream extends InputStream implements SeekableInput {

    private static final Logger logger =
            Logger.getLogger(RandomAccessFileInputStream.class.toString());

    private long m_lMarkPosition;
    private RandomAccessFile m_RandomAccessFile;
    private Object m_oCurrentUser;

    public static RandomAccessFileInputStream build(File file) throws FileNotFoundException {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        RandomAccessFileInputStream rafis = new RandomAccessFileInputStream(raf);
        return rafis;
    }

    protected RandomAccessFileInputStream(RandomAccessFile raf) {
        super();
        m_lMarkPosition = 0L;
        m_RandomAccessFile = raf;
    }


    //
    // InputStream overrides
    //

    public int read() throws IOException {
        return m_RandomAccessFile.read();
    }

    public int read(byte[] buffer) throws IOException {
        return m_RandomAccessFile.read(buffer);
    }

    public int read(byte[] buffer, int offset, int length) throws IOException {
        return m_RandomAccessFile.read(buffer, offset, length);
    }

    public void close() throws IOException {
        m_RandomAccessFile.close();
    }

    public int available() {
        return 0;
    }

    public void mark(int readLimit) {
        try {
            m_lMarkPosition = m_RandomAccessFile.getFilePointer();
        }
        catch (IOException e) {
            throw new RuntimeException(e.toString());
        }
    }

    public boolean markSupported() {
        return true;
    }

    public void reset() throws IOException {
        m_RandomAccessFile.seek(m_lMarkPosition);
    }

    public long skip(long n) throws IOException {
        int nn = (int) (n & 0xFFFFFFFF);
        return (long) m_RandomAccessFile.skipBytes(nn);
    }


    //
    // SeekableInput implementation
    //  (which are not already covered by InputStream overrides)
    //

    public void seekAbsolute(long absolutePosition) throws IOException {
        m_RandomAccessFile.seek(absolutePosition);
    }

    public void seekRelative(long relativeOffset) throws IOException {
        long pos = m_RandomAccessFile.getFilePointer();
        pos += relativeOffset;
        if (pos < 0L)
            pos = 0L;
        m_RandomAccessFile.seek(pos);
    }

    public void seekEnd() throws IOException {
        long end = m_RandomAccessFile.length();
        seekAbsolute(end);
    }

    public long getAbsolutePosition() throws IOException {
        return m_RandomAccessFile.getFilePointer();
    }

    public long getLength() throws IOException {
        return m_RandomAccessFile.length();
    }

    public InputStream getInputStream() {
        return this;
    }

    public synchronized void beginThreadAccess() {

        Object requestingUser = Thread.currentThread();
        while (true) {
            if (m_oCurrentUser == null) {
                m_oCurrentUser = requestingUser;
                break;
            } else if (m_oCurrentUser == requestingUser) {
                break;
            } else { // Some other Thread is currently using us
                try {
                    this.wait(100L);
                }
                catch (InterruptedException ie) {
                }
            }
        }
    }

    public synchronized void endThreadAccess() {

            Object requestingUser = Thread.currentThread();
            if (m_oCurrentUser == null) {
                this.notifyAll();
            } else if (m_oCurrentUser == requestingUser) {
                m_oCurrentUser = null;
                this.notifyAll();
            } else { // Some other Thread is currently using us
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.severe(
                            "ERROR:  Thread finished using SeekableInput, but it wasn't locked by that Thread\n" +
                            "        Thread: " + Thread.currentThread() + "\n" +
                            "        Locking Thread: " + m_oCurrentUser + "\n" +
                            "        SeekableInput: " + this);
                }
            }
        }
    }

