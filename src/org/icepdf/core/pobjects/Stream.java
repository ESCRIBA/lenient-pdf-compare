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

import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGImageDecoder;
import org.icepdf.core.io.BitStream;
import org.icepdf.core.io.ConservativeSizingByteArrayOutputStream;
import org.icepdf.core.io.SeekableInputConstrainedWrapper;
import org.icepdf.core.pobjects.filters.*;
import org.icepdf.core.pobjects.functions.Function;
import org.icepdf.core.pobjects.graphics.*;
import org.icepdf.core.tag.Tagger;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.ImageCache;
import org.icepdf.core.util.Library;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.awt.image.renderable.ParameterBlock;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Stream class is responsible for decoding stream contents and returning
 * either an images object or a byte array depending on the content.  The Stream
 * object worker method is decode which is responsible for decoding the content
 * stream, which is if the first step of the rendering process.  Once a Stream
 * is decoded it is either returned as an image object or a byte array that is
 * then processed further by the ContentParser.
 *
 * @since 1.0
 */
public class Stream extends Dictionary {

    private static final Logger logger =
            Logger.getLogger(Stream.class.toString());

    // original byte stream that has not been decoded
    private SeekableInputConstrainedWrapper streamInput;

    // Images object created from stream
    private ImageCache image = null;
    private final Object imageLock = new Object();

    // reference of stream, needed for encryption support
    private Reference pObjectReference = null;

    // Inline image, from a content stream
    private boolean inlineImage;

    // minimum dimension which will enable image scaling
    private static boolean scaleImages;
    
    // JDK 1.5 imaging order flag and b/r switch
    private static int redIndex = 0;
    private static int blueIndex = 2;

    static {
        // decide if large images will be scaled
        scaleImages =
                Defs.sysPropertyBoolean("org.icepdf.core.scaleImages",
                        true);
        // sniff out jdk 1.5 version
        String version = System.getProperty("java.version");
        if (version.contains("1.5")){
            redIndex  = 2;
            blueIndex = 0;
        }
    }

    private static final int[] GRAY_1_BIT_INDEX_TO_RGB_REVERSED = new int[]{
            0xFFFFFFFF,
            0xFF000000
    };

    private static final int[] GRAY_1_BIT_INDEX_TO_RGB = new int[]{
            0xFF000000,
            0xFFFFFFFF
    };

    private static final int[] GRAY_2_BIT_INDEX_TO_RGB = new int[]{
            0xFF000000,
            0xFF555555,
            0xFFAAAAAA,
            0xFFFFFFFF
    }; // 0. 1 2 3 4 5. 6 7 8 9 A. B C D E F.     0/3, 1/3, 2/3, 3/3

    private static final int[] GRAY_4_BIT_INDEX_TO_RGB = new int[]{
            0xFF000000,
            0xFF111111,
            0xFF222222,
            0xFF333333,
            0xFF444444,
            0xFF555555,
            0xFF666666,
            0xFF777777,
            0xFF888888,
            0xFF999999,
            0xFFAAAAAA,
            0xFFBBBBBB,
            0xFFCCCCCC,
            0xFFDDDDDD,
            0xFFEEEEEE,
            0xFFFFFFFF
    };

    private static final int JPEG_ENC_UNKNOWN_PROBABLY_YCbCr = 0;
    private static final int JPEG_ENC_RGB = 1;
    private static final int JPEG_ENC_CMYK = 2;
    private static final int JPEG_ENC_YCbCr = 3;
    private static final int JPEG_ENC_YCCK = 4;
    private static final int JPEG_ENC_GRAY = 5;

    private static String[] JPEG_ENC_NAMES = new String[]{
            "JPEG_ENC_UNKNOWN_PROBABLY_YCbCr",
            "JPEG_ENC_RGB",
            "JPEG_ENC_CMYK",
            "JPEG_ENC_YCbCr",
            "JPEG_ENC_YCCK",
            "JPEG_ENC_GRAY"
    };

    /**
     * Create a new instance of a Stream.
     *
     * @param l                  library containing a hash of all document objects
     * @param h                  hashtable of parameters specific to the Stream object.
     * @param streamInputWrapper Accessor to stream byte data
     */
    public Stream(Library l, Hashtable h, SeekableInputConstrainedWrapper streamInputWrapper) {
        super(l, h);
        streamInput = streamInputWrapper;
    }

    /**
     * Sets the PObject referece for this stream.  The reference number and
     * generation is need by the encryption algorithm.
     */
    public void setPObjectReference(Reference reference) {
        pObjectReference = reference;
    }

    /**
     * Gets the parent PObject reference for this stream.
     *
     * @return Reference number of parent PObject.
     * @see #setPObjectReference(org.icepdf.core.pobjects.Reference)
     */
    public Reference getPObjectReference() {
        return pObjectReference;
    }

    /**
     * Marks this stream as being constructed from an inline image
     * definition in a content stream
     *
     * @param inlineImage true indicate inline image.
     */
    public void setInlineImage(boolean inlineImage) {
        this.inlineImage = inlineImage;
    }

    /**
     * @return Whether this stream was constructed from an inline image
     *         definition in a content stream
     */
    public boolean isInlineImage() {
        return inlineImage;
    }

    boolean isImageSubtype() {
        Object subtype = library.getObject(entries, "Subtype");
        return subtype != null && subtype.equals("Image");
    }

    /**
     * Utility Method to check if the <code>memoryNeeded</code> can be allocated,
     * and if not, try and free up the needed amount of memory
     *
     * @param memoryNeeded amount of memory to check for availability.
     * @return true if the request number of bytes are free.
     */
    private boolean checkMemory(int memoryNeeded) {
        return library.memoryManager.checkMemory(memoryNeeded);
    }

    /**
     * Utility method for decoding the byte stream using the decode algorithem
     * specified by the filter parameter
     * <p/>
     * The memory manger is called every time a stream is being decoded with an
     * estimated size of the decoded stream.  Because many of the Filter
     * algorithms use compression,  further research must be done to try and
     * find the average amount of memory used by each of the algorithms.
     *
     * @return inputstream that has been decoded as defined by the streams filters.
     */
    public InputStream getInputStreamForDecodedStreamBytes() {
        // Make sure that the stream actually has data to decode, if it doesn't
        // make it null and return.
        if (streamInput == null || streamInput.getLength() < 1) {
            return null;
        }

        long streamLength = streamInput.getLength();
        int memoryNeeded = (int) streamLength;
        checkMemory(memoryNeeded);
        streamInput.prepareForCurrentUse();
        InputStream input = streamInput;

        int bufferSize = Math.min(Math.max((int) streamLength, 64), 16 * 1024);
        input = new java.io.BufferedInputStream(input, bufferSize);

        // Search for crypt dictionary entry and decode params so that
        // named filters can be assigned correctly.
        if (library.securityManager != null) {
            // check see of there is a decodeParams for a crypt filter.
            Hashtable decodeParams = library.getDictionary(entries,"DecodeParam");
            input = library.getSecurityManager().getEncryptionInputStream(
                    getPObjectReference(), library.getSecurityManager().getDecryptionKey(),
                    decodeParams,
                    input, true);
        }

        // Get the filter name for the encoding type, which can be either
        // a Name or Vector.
        Vector filterNames = getFilterNames();
        if (filterNames == null)
            return input;

        // Decode the stream data based on the filter names.
        // Loop through the filterNames and apply the filters in the order
        // in which they where found.
        for (int i = 0; i < filterNames.size(); i++) {
            // grab the name of the filter
            String filterName = filterNames.elementAt(i).toString();
            //System.out.println("  Decoding: " + filterName);

            if (filterName.equals("FlateDecode")
                    || filterName.equals("/Fl")
                    || filterName.equals("Fl")) {
                input = new FlateDecode(library, entries, input);
                memoryNeeded *= 2;
            } else if (
                    filterName.equals("LZWDecode")
                            || filterName.equals("/LZW")
                            || filterName.equals("LZW")) {
                input = new LZWDecode(new BitStream(input), library, entries);
                memoryNeeded *= 2;
            } else if (
                    filterName.equals("ASCII85Decode")
                            || filterName.equals("/A85")
                            || filterName.equals("A85")) {
                input = new ASCII85Decode(input);
                memoryNeeded *= 2;
            } else if (
                    filterName.equals("ASCIIHexDecode")
                            || filterName.equals("/AHx")
                            || filterName.equals("AHx")) {
                input = new ASCIIHexDecode(input);
                memoryNeeded /= 2;
            } else if (
                    filterName.equals("RunLengthDecode")
                            || filterName.equals("/RL")
                            || filterName.equals("RL")) {
                input = new RunLengthDecode(input);
                memoryNeeded *= 2;
            } else if (
                    filterName.equals("CCITTFaxDecode")
                            || filterName.equals("/CCF")
                            || filterName.equals("CCF")) {
                // Leave empty so our else clause works
            } else if (
                    filterName.equals("DCTDecode")
                            || filterName.equals("/DCT")
                            || filterName.equals("DCT")) {
                // Leave empty so our else clause works
            } else if ( // No short name, since no JBIG2 for inline images
                    filterName.equals("JBIG2Decode")) {
                // Leave empty so our else clause works
            } else if ( // No short name, since no JPX for inline images
                    filterName.equals("JPXDecode")) {
                // Leave empty so our else clause works
            } else {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("UNSUPPORTED:" + filterName + " " + entries);
                }
            }
        }

        checkMemory(memoryNeeded);

        return input;
    }

    private byte[] getDecodedStreamBytes() {
        InputStream input = getInputStreamForDecodedStreamBytes();
        if (input == null)
            return null;
        try {
            int outLength = Math.max(1024, (int) streamInput.getLength());
            ConservativeSizingByteArrayOutputStream out = new
                    ConservativeSizingByteArrayOutputStream(outLength, library.memoryManager);
            byte[] buffer = new byte[(outLength > 1024) ? 4096 : 1024];
            while (true) {
                int read = input.read(buffer);
                if (read <= 0)
                    break;
                out.write(buffer, 0, read);
            }
            out.flush();
            out.close();
            // removes this thread from current read,  pottential entry for other thread
            input.close();

            byte[] ret = out.toByteArray();
            return ret;
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Problem decoding stream bytes: ", e);
        }

        return null;
    }

    /**
     * This is similar to getDecodedStreamBytes(), except that the returned byte[]
     * is not necessarily exactly sized, and may be larger. Therefore the returned
     * Integer gives the actual valid size
     *
     * @param presize potencial size to associate with byte array.
     * @return Object[] { byte[] data, Integer sizeActualData }
     */
    private Object[] getDecodedStreamBytesAndSize(int presize) {
        InputStream input = getInputStreamForDecodedStreamBytes();
        if (input == null)
            return null;
        try {
            int outLength;
            if (presize > 0)
                outLength = presize;
            else
                outLength = Math.max(1024, (int) streamInput.getLength());
            ConservativeSizingByteArrayOutputStream out = new
                    ConservativeSizingByteArrayOutputStream(outLength, library.memoryManager);
            byte[] buffer = new byte[(outLength > 1024) ? 4096 : 1024];
            while (true) {
                int read = input.read(buffer);
                if (read <= 0)
                    break;
                out.write(buffer, 0, read);
            }
            out.flush();
            out.close();
            input.close();

            int size = out.size();
            boolean trimmed = out.trim();
            byte[] data = out.relinquishByteArray();
            Object[] ret = new Object[]{data, size};
            return ret;
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Problem decoding stream bytes: ", e);
        }

        return null;
    }

    private void copyDecodedStreamBytesIntoRGB(int[] pixels) {
        byte[] rgb = new byte[3];
        try {
            InputStream input = getInputStreamForDecodedStreamBytes();
            for (int pixelIndex = 0; pixelIndex < pixels.length; pixelIndex++) {
                int argb = 0xFF000000;
                if (input != null) {
                    final int toRead = 3;
                    int haveRead = 0;
                    while (haveRead < toRead) {
                        int currRead = input.read(rgb, haveRead, toRead - haveRead);
                        if (currRead < 0)
                            break;
                        haveRead += currRead;
                    }
                    if (haveRead >= 1)
                        argb |= ((((int) rgb[0]) << 16) & 0x00FF0000);
                    if (haveRead >= 2)
                        argb |= ((((int) rgb[1]) << 8) & 0x0000FF00);
                    if (haveRead >= 3)
                        argb |= (((int) rgb[2]) & 0x000000FF);
                }
                pixels[pixelIndex] = argb;
            }
            if (input != null)
                input.close();
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Problem copying decoding stream bytes: ", e);
        }
    }

    private boolean shouldUseCCITTFaxDecode() {
        return containsFilter(new String[]{"CCITTFaxDecode", "/CCF", "CCF"});
    }

    private boolean shouldUseDCTDecode() {
        return containsFilter(new String[]{"DCTDecode", "/DCT", "DCT"});
    }

    private boolean shouldUseJBIG2Decode() {
        return containsFilter(new String[]{"JBIG2Decode"});
    }

    private boolean shouldUseJPXDecode() {
        return containsFilter(new String[]{"JPXDecode"});
    }

    private boolean containsFilter(String[] searchFilterNames) {
        Vector filterNames = getFilterNames();
        if (filterNames == null)
            return false;
        for (int i = 0; i < filterNames.size(); i++) {
            String filterName = filterNames.elementAt(i).toString();
            for (String search : searchFilterNames) {
                if (search.equals(filterName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Vector getFilterNames() {
        Vector filterNames = null;
        Object o = library.getObject(entries, "Filter");
        if (o instanceof Name) {
            filterNames = new Vector(1);
            filterNames.addElement(o);
        } else if (o instanceof Vector) {
            filterNames = (Vector) o;
        }
        return filterNames;
    }

    private Vector getNormalisedFilterNames() {
        Vector filterNames = getFilterNames();
        if (filterNames == null)
            return null;

        for (int i = 0; i < filterNames.size(); i++) {
            String filterName = filterNames.elementAt(i).toString();

            if (filterName.equals("FlateDecode")
                    || filterName.equals("/Fl")
                    || filterName.equals("Fl")) {
                filterName = "FlateDecode";
            } else if (
                    filterName.equals("LZWDecode")
                            || filterName.equals("/LZW")
                            || filterName.equals("LZW")) {
                filterName = "LZWDecode";
            } else if (
                    filterName.equals("ASCII85Decode")
                            || filterName.equals("/A85")
                            || filterName.equals("A85")) {
                filterName = "ASCII85Decode";
            } else if (
                    filterName.equals("ASCIIHexDecode")
                            || filterName.equals("/AHx")
                            || filterName.equals("AHx")) {
                filterName = "ASCIIHexDecode";
            } else if (
                    filterName.equals("RunLengthDecode")
                            || filterName.equals("/RL")
                            || filterName.equals("RL")) {
                filterName = "RunLengthDecode";
            } else if (
                    filterName.equals("CCITTFaxDecode")
                            || filterName.equals("/CCF")
                            || filterName.equals("CCF")) {
                filterName = "CCITTFaxDecode";
            } else if (
                    filterName.equals("DCTDecode")
                            || filterName.equals("/DCT")
                            || filterName.equals("DCT")) {
                filterName = "DCTDecode";
            }
            // There aren't short names for JBIG2Decode or JPXDecode
            filterNames.set(i, filterName);
        }
        return filterNames;
    }

    /**
     * Despose of references to images and decoded byte streams.
     * Memory optimization
     */
    public void dispose(boolean cache) {
        if (streamInput != null) {
            if (!cache) {
                try {
                    streamInput.dispose();
                }
                catch (IOException e) {
                    logger.log(Level.FINE, "Error disposing stream.", e);
                }
                streamInput = null;
            }else{
                library.removeObject(this.getPObjectReference());
            }
        }
        synchronized (imageLock) {
            if (image != null) {
                image.dispose(cache, (streamInput != null));
                if (!cache || !image.isCachedSomehow()) {
                    image = null;
                }
            }
        }
    }

    /**
     * The DCTDecode filter decodes grayscale or color image data that has been
     * encoded in the JPEG baseline format.  Because DCTDecode only deals
     * with images, the instance of image is update instead of decoded
     * stream.
     *
     * @return buffered images representation of the decoded JPEG data.  Null
     *         if the image could not be properly decoded.
     */
    private BufferedImage dctDecode(
            int width, int height, PColorSpace colourSpace, int bitspercomponent,
            BufferedImage smaskImage, BufferedImage maskImage,
            int[] maskMinRGB, int[] maskMaxRGB, Vector<Integer> decode) {

        // BIS's buffer size should be equal to mark() size, and greater than data size (below)
        InputStream input = getInputStreamForDecodedStreamBytes();
        // Used to just read 1000, but found a PDF that included thumbnails first
        final int MAX_BYTES_TO_READ_FOR_ENCODING = 2048;
        BufferedInputStream bufferedInput = new BufferedInputStream(
                input, MAX_BYTES_TO_READ_FOR_ENCODING);
        bufferedInput.mark(MAX_BYTES_TO_READ_FOR_ENCODING);

        // We don't use the PColorSpace to determine how to decode the JPEG, because it tends to be wrong
        // Some files say DeviceCMYK, or ICCBased, when neither would work, because it's really YCbCrA
        // What does work though, is to look into the JPEG headers themself, via getJPEGEncoding()

        int jpegEncoding = Stream.JPEG_ENC_UNKNOWN_PROBABLY_YCbCr;
        try {
            byte[] data = new byte[MAX_BYTES_TO_READ_FOR_ENCODING];
            int dataRead = bufferedInput.read(data);
            bufferedInput.reset();
            if (dataRead > 0)
                jpegEncoding = getJPEGEncoding(data, dataRead);
        }
        catch (IOException ioe) {
            logger.log(Level.FINE, "Problem determining JPEG type: ", ioe);
        }
        if (Tagger.tagging)
            Tagger.tagImage("DCTDecode_JpegEncoding=" + JPEG_ENC_NAMES[jpegEncoding]);
        //System.out.println("Stream.dctDecode()  objectNumber: " + getPObjectReference().getObjectNumber());
        //System.out.println("Stream.dctDecode()  jpegEncoding: " + JPEG_ENC_NAMES[jpegEncoding]);

        //System.out.println("Stream.dctDecode()  smask: " + smaskImage);
        //System.out.println("Stream.dctDecode()  mask: " + maskImage);
        //System.out.println("Stream.dctDecode()  maskMinRGB: " + maskMinRGB);
        //System.out.println("Stream.dctDecode()  maskMaxRGB: " + maskMaxRGB);

        //long beginUsedMem = (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        //long beginTime = System.currentTimeMillis();
        BufferedImage tmpImage = null;

        if (tmpImage == null) {
            try {
                //System.out.println("Stream.dctDecode()  JPEGImageDecoder");
                JPEGImageDecoder imageDecoder = JPEGCodec.createJPEGDecoder(bufferedInput);

                int bizarreFudge = 64 * 1024 + (int) streamInput.getLength();
                checkMemory(width * height * 8 + bizarreFudge);

                if (jpegEncoding == JPEG_ENC_RGB && bitspercomponent == 8) {
                    //System.out.println("Stream.dctDecode()    JPEG_ENC_RGB");
                    Raster r = imageDecoder.decodeAsRaster();
                    WritableRaster wr = (r instanceof WritableRaster)
                            ? (WritableRaster) r : r.createCompatibleWritableRaster();
                    //System.out.println("Stream.dctDecode()      EncodedColorID: " + imageDecoder.getJPEGDecodeParam().getEncodedColorID());
                    alterRasterRGB2PColorSpace(wr, colourSpace);
                    tmpImage = makeRGBBufferedImage(wr);
                } else if (jpegEncoding == JPEG_ENC_CMYK && bitspercomponent == 8) {
                    //System.out.println("Stream.dctDecode()    JPEG_ENC_CMYK");
                    Raster r = imageDecoder.decodeAsRaster();
                    WritableRaster wr = (r instanceof WritableRaster)
                            ? (WritableRaster) r : r.createCompatibleWritableRaster();
                    //System.out.println("Stream.dctDecode()      EncodedColorID: " + imageDecoder.getJPEGDecodeParam().getEncodedColorID());
                    tmpImage = alterRasterCMYK2BGRA(wr, smaskImage, maskImage); //TODO Use maskMinRGB, maskMaxRGB or orig comp version here
                } else if (jpegEncoding == JPEG_ENC_YCbCr && bitspercomponent == 8) {
                    //System.out.println("Stream.dctDecode()    JPEG_ENC_YCbCr");
                    Raster r = imageDecoder.decodeAsRaster();
                    WritableRaster wr = (r instanceof WritableRaster)
                            ? (WritableRaster) r : r.createCompatibleWritableRaster();
                    //System.out.println("Stream.dctDecode()      EncodedColorID: " + imageDecoder.getJPEGDecodeParam().getEncodedColorID());
                    tmpImage = alterRasterYCbCr2RGB(wr, smaskImage, maskImage, decode, bitspercomponent);
                } else if (jpegEncoding == JPEG_ENC_YCCK && bitspercomponent == 8) {
                    //System.out.println("Stream.dctDecode()    JPEG_ENC_YCCK");
                    Raster r = imageDecoder.decodeAsRaster();
                    WritableRaster wr = (r instanceof WritableRaster)
                            ? (WritableRaster) r : r.createCompatibleWritableRaster();
                    //System.out.println("Stream.dctDecode()      EncodedColorID: " + imageDecoder.getJPEGDecodeParam().getEncodedColorID());
                    // YCCK to RGB works better if an CMYK intermediate is used, but slower.
                    alterRasterYCCK2CMYK(wr, decode,bitspercomponent);
                    tmpImage = alterRasterCMYK2BGRA(wr, smaskImage, maskImage);
                } else if (jpegEncoding == JPEG_ENC_GRAY && bitspercomponent == 8) {
                    //System.out.println("Stream.dctDecode()    JPEG_ENC_GRAY");
                    Raster r = imageDecoder.decodeAsRaster();
                    WritableRaster wr = (r instanceof WritableRaster)
                            ? (WritableRaster) r : r.createCompatibleWritableRaster();
                    //System.out.println("Stream.dctDecode()      EncodedColorID: " + imageDecoder.getJPEGDecodeParam().getEncodedColorID());
                    // In DCTDecode with ColorSpace=DeviceGray, the samples are gray values (2000_SID_Service_Info.core)
                    // In DCTDecode with ColorSpace=Separation, the samples are Y values (45-14550BGermanForWeb.core AKA 4570.core)
                    // Instead of assuming that Separation is special, I'll assume that DeviceGray is
                    if (!(colourSpace instanceof DeviceGray) &&
                            !(colourSpace instanceof ICCBased)) {
                        if (Tagger.tagging)
                            Tagger.tagImage("DCTDecode_JpegSubEncoding=Y");
                        alterRasterY2Gray(wr, bitspercomponent, decode); //TODO Use smaskImage, maskImage, maskMinRGB, maskMaxRGB or orig comp version here
                    }
                    tmpImage = makeGrayBufferedImage(wr);
                    // apply mask value
                    if (maskImage != null){
                        applyExplicitMask(tmpImage, maskImage);
                    }
                } else {
                    //System.out.println("Stream.dctDecode()    Other");
                    //tmpImage = imageDecoder.decodeAsBufferedImage();
                    Raster r = imageDecoder.decodeAsRaster();
                    WritableRaster wr = (r instanceof WritableRaster)
                            ? (WritableRaster) r : r.createCompatibleWritableRaster();
                    //System.out.println("Stream.dctDecode()      EncodedColorID: " + imageDecoder.getJPEGDecodeParam().getEncodedColorID());
                    if (imageDecoder.getJPEGDecodeParam().getEncodedColorID() ==
                            com.sun.image.codec.jpeg.JPEGDecodeParam.COLOR_ID_YCbCrA) {
                        if (Tagger.tagging)
                            Tagger.tagImage("DCTDecode_JpegSubEncoding=YCbCrA");
                        // YCbCrA, which is slightly different than YCCK
                        alterRasterYCbCrA2RGBA_new(wr, smaskImage, maskImage,
                                decode, bitspercomponent); //TODO Use maskMinRGB, maskMaxRGB or orig comp version here
                        tmpImage = makeRGBABufferedImage(wr);
                    } else {
                        if (Tagger.tagging)
                            Tagger.tagImage("DCTDecode_JpegSubEncoding=YCbCr");
                        alterRasterYCbCr2RGB(wr, smaskImage, maskImage, decode, bitspercomponent);
                        tmpImage = makeRGBBufferedImage(wr);
                        // special case to handle an smask on an RGB image.  In
                        // such a case we need to copy the rgb and soft mask effect
                        // to th new ARGB image.
                        if (smaskImage != null) {
                            BufferedImage argbImage = new BufferedImage(width,
                                    height, BufferedImage.TYPE_INT_ARGB);
                            int[] srcBand = new int[width];
                            int[] sMaskBand = new int[width];
                            // iterate over each band to apply the mask
                            for (int i = 0; i < height; i++) {
                                tmpImage.getRGB(0, i, width, 1, srcBand, 0, width);
                                smaskImage.getRGB(0, i, width, 1, sMaskBand, 0, width);
                                // apply the soft mask blending
                                for (int j = 0; j < width; j++) {
                                    sMaskBand[j] = ((sMaskBand[j] & 0xff) << 24)
                                            | (srcBand[j] & ~0xff000000);
                                }
                                argbImage.setRGB(0, i, width, 1, sMaskBand, 0, width);
                            }
                            tmpImage.flush();
                            tmpImage = argbImage;
                        }
                    }
                }
            }
            catch (Exception e) {
                logger.log(Level.FINE, "Problem loading JPEG image via JPEGImageDecoder: ", e);
            }
            if (tmpImage != null) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=DCTDecode_SunJPEGImageDecoder");
            }
        }

        try {
            bufferedInput.close();
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Error closing image stream.", e);
        }

        if (tmpImage == null) {
            try {
                //System.out.println("Stream.dctDecode()  JAI");
                Object javax_media_jai_RenderedOp_op = null;
                try {
                    // Have to reget the data
                    input = getInputStreamForDecodedStreamBytes();

                    /*
                    com.sun.media.jai.codec.SeekableStream s = com.sun.media.jai.codec.SeekableStream.wrapInputStream( new ByteArrayInputStream(data), true );
                    ParameterBlock pb = new ParameterBlock();
                    pb.add( s );
                    javax.media.jai.RenderedOp op = javax.media.jai.JAI.create( "jpeg", pb );
                    */
                    Class ssClass = Class.forName("com.sun.media.jai.codec.SeekableStream");
                    Method ssWrapInputStream = ssClass.getMethod("wrapInputStream", InputStream.class, Boolean.TYPE);
                    Object com_sun_media_jai_codec_SeekableStream_s =
                            ssWrapInputStream.invoke(null, input, Boolean.TRUE);
                    ParameterBlock pb = new ParameterBlock();
                    pb.add(com_sun_media_jai_codec_SeekableStream_s);
                    Class jaiClass = Class.forName("javax.media.jai.JAI");
                    Method jaiCreate = jaiClass.getMethod("create", String.class, ParameterBlock.class);
                    javax_media_jai_RenderedOp_op = jaiCreate.invoke(null, "jpeg", pb);
                }
                catch (Exception e) {
                }

                if (javax_media_jai_RenderedOp_op != null) {
                    if (jpegEncoding == JPEG_ENC_CMYK && bitspercomponent == 8) {
                        /*
                         * With or without alterRasterCMYK2BGRA(), give blank image
                        Raster r = op.copyData();
                        WritableRaster wr = (r instanceof WritableRaster)
                                ? (WritableRaster) r : r.createCompatibleWritableRaster();
                        alterRasterCMYK2BGRA( wr );
                        tmpImage = makeRGBABufferedImage( wr );
                        */
                        /*
                         * With alterRasterCMYK2BGRA() colors gibbled, without is blank
                         * Slower, uses more memory, than JPEGImageDecoder
                        BufferedImage img = op.getAsBufferedImage();
                        WritableRaster wr = img.getRaster();
                        alterRasterCMYK2BGRA( wr );
                        tmpImage = img;
                        */
                    } else if (jpegEncoding == JPEG_ENC_YCCK && bitspercomponent == 8) {
                        /*
                         * This way, with or without alterRasterYCbCrA2BGRA(), give blank image
                        Raster r = op.getData();
                        WritableRaster wr = (r instanceof WritableRaster)
                                ? (WritableRaster) r : r.createCompatibleWritableRaster();
                        alterRasterYCbCrA2BGRA( wr );
                        tmpImage = makeRGBABufferedImage( wr );
                        */
                        /*
                         * With alterRasterYCbCrA2BGRA() colors gibbled, without is blank
                         * Slower, uses more memory, than JPEGImageDecoder
                        BufferedImage img = op.getAsBufferedImage();
                        WritableRaster wr = img.getRaster();
                        alterRasterYCbCrA2BGRA( wr );
                        tmpImage = img;
                        */
                    } else {
                        //System.out.println("Stream.dctDecode()    Other");
                        /* tmpImage = op.getAsBufferedImage(); */
                        Class roClass = Class.forName("javax.media.jai.RenderedOp");
                        Method roGetAsBufferedImage = roClass.getMethod("getAsBufferedImage");
                        tmpImage = (BufferedImage) roGetAsBufferedImage.invoke(javax_media_jai_RenderedOp_op);
                        if (tmpImage != null) {
                            if (Tagger.tagging)
                                Tagger.tagImage("HandledBy=DCTDecode_JAI");
                        }
                    }
                }
            }
            catch (Exception e) {
                logger.log(Level.FINE, "Problem loading JPEG image via JAI: ", e);
            }

            try {
                input.close();
            }
            catch (IOException e) {
                logger.log(Level.FINE, "Problem closing image stream. ", e);
            }
        }

        if (tmpImage == null) {
            try {
                //System.out.println("Stream.dctDecode()  Toolkit");
                byte[] data = getDecodedStreamBytes();
                if (data != null) {
                    Image img = Toolkit.getDefaultToolkit().createImage(data);
                    if (img != null) {
                        tmpImage = makeRGBABufferedImageFromImage(img);
                        if (Tagger.tagging)
                            Tagger.tagImage("HandledBy=DCTDecode_ToolkitCreateImage");
                    }
                }
            }
            catch (Exception e) {
                logger.log(Level.FINE, "Problem loading JPEG image via Toolkit: ", e);
            }
        }

        //long endUsedMem = (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        //long endTime = System.currentTimeMillis();
        //System.out.println("Mem used: " + (endUsedMem-beginUsedMem) + ",\ttime: " + (endTime-beginTime));
        return tmpImage;
    }

    /**
     * Utility method to decode JBig2 images.
     *
     * @param width  width of image
     * @param height height of image
     * @return buffered image of decoded jbig2 image stream.   Null if an error
     *         occured during decode.
     */
    private BufferedImage jbig2Decode(int width, int height, Color fill) {
        BufferedImage tmpImage = null;

        try {
            Class jbig2DecoderClass = Class.forName("org.jpedal.jbig2.JBIG2Decoder");
            // create instance of decoder
            Constructor jbig2DecoderClassConstructor =
                        jbig2DecoderClass.getDeclaredConstructor();
            Object jbig2Decoder = jbig2DecoderClassConstructor.newInstance();

            checkMemory(108 * 1024);
            // get the decode params form the stream
            Hashtable decodeParms = library.getDictionary(entries, "DecodeParms");
            if (decodeParms != null) {
                Stream globalsStream = null;
                Object jbigGlobals = library.getObject(decodeParms, "JBIG2Globals");
                if (jbigGlobals instanceof Stream){
                    globalsStream = (Stream) library.getObject(decodeParms, "JBIG2Globals");
                }
                if (globalsStream != null){
                    byte[] globals = globalsStream.getDecodedStreamBytes();
                    if (globals != null && globals.length > 0 ) {
                        // invoked ecoder.setGlobalData(globals);
                        Class partypes[] = new Class[1];
                        partypes[0] = byte[].class;
                        Object arglist[] = new Object[1];
                        arglist[0] = globals;
                        Method setGlobalData =
                                jbig2DecoderClass.getMethod("setGlobalData", partypes);
                        setGlobalData.invoke(jbig2Decoder, arglist);
                        globals = null;
                    }
                }
            }
            // decode the data stream, decoder.decodeJBIG2(data);
            byte[] data = getDecodedStreamBytes();
            checkMemory((width + 8) * height * 22 / 10); // Between 0.5 and 2.2
            Class argTypes[] = new Class[]{byte[].class};
            Object arglist[] = new Object[]{data};
            Method decodeJBIG2 = jbig2DecoderClass.getMethod("decodeJBIG2", argTypes);
            decodeJBIG2.invoke(jbig2Decoder, arglist);
            data = null;
            // From decoding, memory usage increases more than (width*height/8),
            // due to intermediate JBIG2Bitmap objects, used to build the final
            // one, still hanging around. Cleanup intermediate data-structures.
            // decoder.cleanupPostDecode();
            Method cleanupPostDecode = jbig2DecoderClass.getMethod("cleanupPostDecode");
            cleanupPostDecode.invoke(jbig2Decoder);

            // final try an fetch the image. tmpImage = decoder.getPageAsBufferedImage(0);
            checkMemory((width + 8) * height / 8);
            argTypes = new Class[]{Integer.TYPE};
            arglist = new Object[]{0};
            Method getPageAsBufferedImage = jbig2DecoderClass.getMethod("getPageAsBufferedImage", argTypes);
            tmpImage = (BufferedImage)getPageAsBufferedImage.invoke(jbig2Decoder, arglist);
        }catch (ClassNotFoundException e) {
            logger.warning("JBIG2 image library could not be found");
        }
        catch (Exception e) {
            logger.log(Level.WARNING, "Problem loading JBIG2 image: ", e);
        }
        return tmpImage;
    }

    /**
     * Creates a new instance of a Dictionary.
     *
     * @param library document library.
     * @param entries dictionary entries.
     */
    public Stream(Library library, Hashtable entries) {
        super(library, entries);    //To change body of overridden methods use File | Settings | File Templates.
    }

    /**
     * Utility method to decode JPEG2000 images.
     *
     * @param width            width of image.
     * @param height           height of image.
     * @param colourSpace      colour space to apply to image.
     * @param bitsPerComponent bits used to represent a colour
     * @param fill             fill colour used in last draw operand.
     * @param maskImage        image mask if any, can be null.
     * @param sMaskImage       image smask if any, can be null.
     * @param maskMinRGB       mask minimum rgb value, optional.
     * @param maskMaxRGB       mask maximum rgb value, optional.
     * @return buffered image of the jpeg2000 image stream.  Null if a problem
     *         occurred during the decode.
     */
    private BufferedImage jpxDecode(int width, int height, PColorSpace colourSpace,
                                    int bitsPerComponent, Color fill,
                                    BufferedImage sMaskImage, BufferedImage maskImage,
                                    int[] maskMinRGB, int[] maskMaxRGB, Vector decode) {
        BufferedImage tmpImage = null;
        try {
            // Verify that ImageIO can read JPEG2000
            Iterator<ImageReader> iterator = ImageIO.getImageReadersByFormatName("JPEG2000");
            if (!iterator.hasNext()) {
                logger.info(
                        "ImageIO missing required plug-in to read JPEG 2000 images. " +
                                "You can download the JAI ImageIO Tools from: " +
                                "https://jai-imageio.dev.java.net/");
                return null;
            }
            // decode the image.
            byte[] data = getDecodedStreamBytes();
            ImageInputStream imageInputStream = ImageIO.createImageInputStream(
                    new ByteArrayInputStream(data));
            tmpImage = ImageIO.read(imageInputStream);

            // check for an instance of ICCBased, we don't currently support
            // this colour mode well so we'll used the alternative colour
            if (colourSpace instanceof ICCBased) {
                ICCBased iccBased = (ICCBased) colourSpace;
                if (iccBased.getAlternate() != null) {
                    // set the alternate as the current
                    colourSpace = iccBased.getAlternate();
                }
                // try to process the ICC colour space
                else {
                    ColorSpace cs = iccBased.getColorSpace();
                    ColorConvertOp cco = new ColorConvertOp(cs, null);
                    tmpImage = cco.filter(tmpImage, null);
                }
            }

            // apply respective colour models to the JPEG2000 image.
            if (colourSpace instanceof DeviceRGB && bitsPerComponent == 8) {
                WritableRaster wr = tmpImage.getRaster();
                alterRasterRGB2PColorSpace(wr, colourSpace);
                tmpImage = makeRGBBufferedImage(wr);
            } else if (colourSpace instanceof DeviceCMYK && bitsPerComponent == 8) {
                WritableRaster wr = tmpImage.getRaster();
                tmpImage = alterRasterCMYK2BGRA(wr, sMaskImage, maskImage);
            } else if ((colourSpace instanceof DeviceGray ||
                    colourSpace instanceof Indexed)
                    && bitsPerComponent == 8) {
                WritableRaster wr = tmpImage.getRaster();
                tmpImage = makeGrayBufferedImage(wr);
            } else if (colourSpace instanceof Separation){
                WritableRaster wr = tmpImage.getRaster();
                alterRasterY2Gray(wr, bitsPerComponent, decode);
            }

            // check for a mask value
            if (maskImage != null) {
                applyExplicitMask(tmpImage, maskImage);
            }

        }
        catch (IOException e) {
            logger.log(Level.FINE, "Problem loading JPEG2000 image: ", e);
        }

        return tmpImage;
    }

    private static BufferedImage alterRasterCMYK2BGRA(WritableRaster wr,
                                                      BufferedImage smaskImage,
                                                      BufferedImage maskImage) {

        int width = wr.getWidth();
        int height = wr.getHeight();

        Raster smaskRaster = null;
        int smaskWidth = 0;
        int smaskHeight = 0;
        if (smaskImage != null) {
            smaskRaster = smaskImage.getRaster();
            smaskWidth = smaskRaster.getWidth();
            smaskHeight = smaskRaster.getHeight();
            // If smask is larger then the image, and needs to be scaled to match the image.
            if (width < smaskWidth || height < smaskHeight) {
                // calculate scale factors.
                double scaleX = width / (double) smaskWidth;
                double scaleY = height / (double) smaskHeight;
                // scale the mask to match the base image.
                AffineTransform tx = new AffineTransform();
                tx.scale(scaleX, scaleY);
                AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                BufferedImage sbim = op.filter(smaskImage, null);
                smaskImage.flush();
                smaskImage = sbim;
            }
            // update the new deminsions.
            smaskRaster = smaskImage.getRaster();
            smaskWidth = smaskRaster.getWidth();
            smaskHeight = smaskRaster.getHeight();
        }

        Raster maskRaster = null;
        int maskWidth = 0;
        int maskHeight = 0;
        if (maskImage != null) {
            maskRaster = maskImage.getRaster();
            maskWidth = maskRaster.getWidth();
            maskHeight = maskRaster.getHeight();
        }

        // this convoluted cymk->rgba method is from DeviceCMYK class.
        float inCyan, inMagenta, inYellow, inBlack;
        float lastCyan = -1, lastMagenta = -1, lastYellow = -1, lastBlack = -1;
        double c, m, y2, aw, ac, am, ay, ar, ag, ab;
        float outRed, outGreen, outBlue;
        int rValue = 0, gValue = 0, bValue = 0, alpha = 0;
        int[] values = new int[4];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, values);

                inCyan = values[0] / 255.0f;
                inMagenta = values[1] / 255.0f;
                inYellow = values[2] / 255.0f;
                // lessen the amount of black, standard 255 fraction is too dark
                // increasing the denominator has the same affect of lighting up
                // the image.
                inBlack = (values[3] / 768.0f);

                if (!(inCyan == lastCyan && inMagenta == lastMagenta &&
                        inYellow == lastYellow && inBlack == lastBlack)){

                    c = clip(0, 1, inCyan + inBlack);
                    m = clip(0, 1, inMagenta + inBlack);
                    y2 = clip(0, 1, inYellow + inBlack);
                    aw = (1 - c) * (1 - m) * (1 - y2);
                    ac = c * (1 - m) * (1 - y2);
                    am = (1 - c) * m * (1 - y2);
                    ay = (1 - c) * (1 - m) * y2;
                    ar = (1 - c) * m * y2;
                    ag = c * (1 - m) * y2;
                    ab = c * m * (1 - y2);

                    outRed = (float)clip(0, 1, aw + 0.9137 * am + 0.9961 * ay + 0.9882 * ar);
                    outGreen = (float)clip(0, 1, aw + 0.6196 * ac + ay + 0.5176 * ag);
                    outBlue = (float)clip(0, 1, aw + 0.7804 * ac + 0.5412 * am + 0.0667 * ar + 0.2118 * ag + 0.4863 * ab);
                    rValue = (int) (outRed * 255);
                    gValue = (int) (outGreen * 255);
                    bValue = (int) (outBlue * 255);
                    alpha = 0xFF;
                }
                lastCyan = inCyan;
                lastMagenta  = inMagenta;
                lastYellow = inYellow;
                lastBlack =  inBlack;

                // this fits into the larger why are we doing this on a case by case basis.
                if (y < smaskHeight && x < smaskWidth && smaskRaster != null){
                    alpha = (smaskRaster.getSample(x, y, 0) & 0xFF);
                }else if (y < maskHeight && x < maskWidth && maskRaster != null) {
                    // When making an ImageMask, the alpha channel is setup so that
                    //  it both works correctly for the ImageMask being painted,
                    //  and also for when it's used here, to determine the alpha
                    //  of an image that it's masking
                    alpha = (maskImage.getRGB(x, y) >>> 24) & 0xFF; // Extract Alpha from ARGB
                }
                values[redIndex] = rValue;
                values[1] = gValue;
                values[blueIndex] = bValue;
                values[3] = alpha;
                wr.setPixel(x, y, values);
            }
        }
        // apply the soft mask, but first we need an rgba image,
        // this is pretty expensive, would like to find quicker method.
        BufferedImage tmpImage = makeRGBABufferedImage(wr);
        if (smaskImage != null) {
            BufferedImage argbImage = new BufferedImage(width,
                    height, BufferedImage.TYPE_INT_ARGB);
            int[] srcBand = new int[width];
            int[] sMaskBand = new int[width];
            // iterate over each band to apply the mask
            for (int i = 0; i < height; i++) {
                tmpImage.getRGB(0, i, width, 1, srcBand, 0, width);
                smaskImage.getRGB(0, i, width, 1, sMaskBand, 0, width);
                // apply the soft mask blending
                for (int j = 0; j < width; j++) {
                    sMaskBand[j] = ((sMaskBand[j] & 0xff) << 24)
                            | (srcBand[j] & ~0xff000000);
                }
                argbImage.setRGB(0, i, width, 1, sMaskBand, 0, width);
            }
            tmpImage.flush();
            tmpImage = argbImage;
        }
        return tmpImage;
    }

    private static BufferedImage alterRasterYCbCr2RGB(WritableRaster wr,
                BufferedImage smaskImage, BufferedImage maskImage,
                Vector<Integer> decode, int bitsPerComponent) {
        float[] values;
        int width = wr.getWidth();
        int height = wr.getHeight();
        int maxValue = ((int) Math.pow(2, bitsPerComponent)) - 1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // apply decode param.
                values = getNormalizedComponents(
                        (byte[])wr.getDataElements(x,y,null),
                        decode,
                        maxValue);

                float Y = values[0] * 255;
                float Cb = values[1] * 255;
                float Cr = values[2] * 255;

                float Cr_128 = Cr - 128;
                float Cb_128 = Cb - 128;

                float rVal = Y + (1370705 * Cr_128 / 1000000);
                float gVal = Y - (337633 * Cb_128 / 1000000) - (698001 * Cr_128 / 1000000);
                float bVal = Y + (1732446 * Cb_128 / 1000000);

                byte rByte = (rVal < 0) ? (byte) 0 : (rVal > 255) ? (byte) 0xFF : (byte) rVal;
                byte gByte = (gVal < 0) ? (byte) 0 : (gVal > 255) ? (byte) 0xFF : (byte) gVal;
                byte bByte = (bVal < 0) ? (byte) 0 : (bVal > 255) ? (byte) 0xFF : (byte) bVal;

                // apply mask and smask values.

                values[0] = rByte;
                values[1] = gByte;
                values[2] = bByte;

                wr.setPixel(x, y, values);
            }
        }
        BufferedImage tmpImage = makeRGBBufferedImage(wr);
        // special case to handle an smask on an RGB image.  In
        // such a case we need to copy the rgb and soft mask effect
        // to th new ARGB image.
        if (smaskImage != null) {
            BufferedImage argbImage = new BufferedImage(width,
                    height, BufferedImage.TYPE_INT_ARGB);
            int[] srcBand = new int[width];
            int[] sMaskBand = new int[width];
            // iterate over each band to apply the mask
            for (int i = 0; i < height; i++) {
                tmpImage.getRGB(0, i, width, 1, srcBand, 0, width);
                smaskImage.getRGB(0, i, width, 1, sMaskBand, 0, width);
                // apply the soft mask blending
                for (int j = 0; j < width; j++) {
                    sMaskBand[j] = ((sMaskBand[j] & 0xff) << 24)
                            | (srcBand[j] & ~0xff000000);
                }
                argbImage.setRGB(0, i, width, 1, sMaskBand, 0, width);
            }
            tmpImage.flush();
            tmpImage = argbImage;
        }
        return tmpImage;
    }

    /**
     * The basic idea is that we do a fuzzy colour conversion from YCCK to
     * BGRA.  The conversion is not perfect giving a bit of a greenish hue to the
     * image in question.  I've tweaked the core Adobe algorithm ot give slightly
     * "better" colour representation but it does seem to make red a little light.
     * @param wr  image stream to convert colour space.
     * @param smaskImage smask used to apply alpha values.
     * @param maskImage maks image for drop out.
     */
    private static void alterRasterYCCK2BGRA(WritableRaster wr,
                                             BufferedImage smaskImage,
                                             BufferedImage maskImage,
                                             Vector<Integer> decode,
                                             int bitsPerComponent) {
        Raster smaskRaster = null;
        int smaskWidth = 0;
        int smaskHeight = 0;
        if (smaskImage != null) {
            smaskRaster = smaskImage.getRaster();
            smaskWidth = smaskRaster.getWidth();
            smaskHeight = smaskRaster.getHeight();
        }

        Raster maskRaster = null;
        int maskWidth = 0;
        int maskHeight = 0;
        if (maskImage != null) {
            maskRaster = maskImage.getRaster();
            maskWidth = maskRaster.getWidth();
            maskHeight = maskRaster.getHeight();
        }

        float[] origValues;
        double[] rgbaValues = new double[4];

        int width = wr.getWidth();
        int height = wr.getHeight();
        int maxValue = ((int) Math.pow(2, bitsPerComponent)) - 1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // apply decode param.
                origValues = getNormalizedComponents(
                        (byte[])wr.getDataElements(x,y,null),
                        decode,
                        maxValue);

                float Y = origValues[0] * 255;
                float Cb = origValues[1] * 255;
                float Cr = origValues[2] * 255;
//                float K = origValues[3] * 255;

                // removing alteration for now as some samples are too dark.
               // Y *= .95; // gives a darker image,  as y approaches zero,
                         // the image becomes darke

                float Cr_128 = Cr - 128;
                float Cb_128 = Cb - 128;

                // adobe conversion for CCIR Rec. 601-1 standard.
                // http://partners.adobe.com/public/developer/en/ps/sdk/5116.DCT_Filter.pdf
//                double rVal = Y + (1.4020 * Cr_128);
//                double gVal = Y - (.3441363 * Cb_128) - (.71413636 * Cr_128);
//                double bVal = Y + (1.772 * Cb_128);

                // intel codecs, http://software.intel.com/sites/products/documentation/hpc/ipp/ippi/ippi_ch6/ch6_color_models.html
                // Intel IPP conversion for JPEG codec.
//                double rVal = Y + (1.402 * Cr) - 179.456;
//                double gVal = Y - (0.34414 * Cb) - (.71413636 * Cr) + 135.45984;
//                double bVal = Y + (1.772 * Cb) - 226.816;

                // ICEsoft custom algorithm, results may vary, res are a little
                // off but over all a better conversion/ then the stoke algorithms.
                double rVal = Y + (1.4020 * Cr_128);
                double gVal = Y + (.14414 * Cb_128) + (.11413636  * Cr_128);
                double bVal = Y + (1.772 * Cb_128);

                // Intel IPP conversion for ITU-R BT.601 for video
                // default 16, higher more green and darker blacks, lower less
                // green hue and lighter blacks.
//                double kLight = (1.164 * (Y -16 ));
//                double rVal = kLight + (1.596 * Cr_128);
//                double gVal = kLight - (0.392 * Cb_128) - (0.813 * Cr_128);
//                double bVal = kLight + (1.017 * Cb_128);
                // intel PhotoYCC Color Model [0.1],  not a likely candidate for jpegs.
//                double y1 = Y/255.0;
//                double c1 = Cb/255.0;
//                double c2 = Cr/255.0;
//                double rVal = ((0.981 * y1) + (1.315 * (c2 - 0.537))) *255.0;
//                double gVal = ((0.981 * y1) - (0.311 * (c1 - 0.612))- (0.669 * (c2 - 0.537))) *255.0;
//                double bVal = ((0.981 * y1) + (1.601 * (c1 - 0.612))) *255.0;

                // check the range an convert as needed.
                byte rByte = (rVal < 0) ? (byte) 0 : (rVal > 255) ? (byte) 0xFF : (byte) rVal;
                byte gByte = (gVal < 0) ? (byte) 0 : (gVal > 255) ? (byte) 0xFF : (byte) gVal;
                byte bByte = (bVal < 0) ? (byte) 0 : (bVal > 255) ? (byte) 0xFF : (byte) bVal;
                int alpha = 0xFF;
                if (y < smaskHeight && x < smaskWidth && smaskRaster != null){
                    alpha = (smaskRaster.getSample(x, y, 0) & 0xFF);
                }else if (y < maskHeight && x < maskWidth && maskRaster != null) {
                    // When making an ImageMask, the alpha channel is setup so that
                    //  it both works correctly for the ImageMask being painted,
                    //  and also for when it's used here, to determine the alpha
                    //  of an image that it's masking
                    alpha = (maskImage.getRGB(x, y) >>> 24) & 0xFF; // Extract Alpha from ARGB
                }

                rgbaValues[0] = bByte;
                rgbaValues[1] = gByte;
                rgbaValues[2] = rByte;
                rgbaValues[3] = alpha;

                wr.setPixel(x, y, rgbaValues);
            }
        }
    }

    /**
     * The basic idea is that we do a fuzzy colour conversion from YCCK to
     * CMYK.  The conversion is not perfect but when converted again from
     * CMYK to RGB the result is much better then going directly from YCCK to
     * RGB.
     * NOTE: no masking here, as it is done later in the call to
     * {@see alterRasterCMYK2BGRA}
     * @param wr writable raster to alter.
     * @param decode decode vector.
     * @param bitsPerComponent bits per component .
     */
    private static void alterRasterYCCK2CMYK(WritableRaster wr,
                                             Vector<Integer> decode,
                                             int bitsPerComponent) {

        float[] origValues;
        double[] pixels = new double[4];
        double Y,Cb,Cr,K;
        double lastY = -1,lastCb = -1,lastCr = -1,lastK = -1;
        double c = 0, m = 0, y2 = 0, k = 0;

        int width = wr.getWidth();
        int height = wr.getHeight();
        int maxValue = ((int) Math.pow(2, bitsPerComponent)) - 1;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // apply decode param.
                origValues = getNormalizedComponents(
                        (byte[])wr.getDataElements(x,y,null),
                        decode,
                        maxValue);

                Y = origValues[0] * 255;
                Cb = origValues[1] * 255;
                Cr = origValues[2]* 255;
                K = origValues[3]* 255;

                if (!(lastY == y && lastCb == Cb && lastCr == Cr && lastK == K )){

                    // intel codecs, http://software.intel.com/sites/products/documentation/hpc/ipp/ippi/ippi_ch6/ch6_color_models.html
                    // Intel IPP conversion for JPEG codec.
                    c = 255 - (Y + (1.402 * Cr) - 179.456 );
                    m = 255 - (Y - (0.34414 * Cb) - (0.71413636 * Cr) + 135.45984);
                    y2 = 255 - (Y + (1.7718 * Cb) - 226.816);
                    k = K;

                    c = clip(0,255,c);
                    m = clip(0,255,m);
                    y2 = clip(0,255,y2);
                }

                lastY = Y;
                lastCb = Cb;
                lastCr = Cr;
                lastK = K;

                pixels[0] = c;
                pixels[1] = m;
                pixels[2] = y2;
                pixels[3] = k;

                wr.setPixel(x, y, pixels);
            }
        }
    }

    /**
     * Apply the Decode Array domain for each colour component.
     * @param pixels colour to process by decode
     * @param decode decode array for colour space
     * @param xMax domain max for the second point on the interpolation line
     * always (2<sup>bitsPerComponent</sup> - 1).
     * @return
     */
    private static float[] getNormalizedComponents(
            byte[] pixels,
            Vector decode, int xMax) {
        float[] normComponents = new float[pixels.length];
        int val;
        float yMin;
        float yMax;
        // interpolate each colour component for the given decode domain.
        for (int i = 0; i < pixels.length; i++) {
            val = pixels[i] & 0xff;
            yMin = ((Number)decode.get(i * 2)).floatValue();
            yMax = ((Number)decode.get((i * 2) + 1)).floatValue();
            normComponents[i] =
                    Function.interpolate(val, 0, xMax, yMin, yMax);
        }
        return normComponents;
    }


    private static void alterRasterYCbCrA2RGBA_new(WritableRaster wr,
                BufferedImage smaskImage, BufferedImage maskImage,
                Vector<Integer> decode, int bitsPerComponent) {
        Raster smaskRaster = null;
        int smaskWidth = 0;
        int smaskHeight = 0;
        if (smaskImage != null) {
            smaskRaster = smaskImage.getRaster();
            smaskWidth = smaskRaster.getWidth();
            smaskHeight = smaskRaster.getHeight();
        }

        Raster maskRaster = null;
        int maskWidth = 0;
        int maskHeight = 0;
        if (maskImage != null) {
            maskRaster = maskImage.getRaster();
            maskWidth = maskRaster.getWidth();
            maskHeight = maskRaster.getHeight();
        }

        float[] origValues = new float[4];
        int[] rgbaValues = new int[4];
        int width = wr.getWidth();
        int height = wr.getHeight();
        int maxValue = ((int) Math.pow(2, bitsPerComponent)) - 1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, origValues);
                // apply decode param.
                // couldn't quite get this one right, doesn't decode
                // as I would have thought.
//                origValues = getNormalizedComponents(
//                        (byte[])wr.getDataElements(x,y,null),
//                        decode,
//                        maxValue);

                float Y = origValues[0];
                float Cb = origValues[1];
                float Cr = origValues[2];
                float K = origValues[3];
                Y = K - Y;
                float Cr_128 = Cr - 128;
                float Cb_128 = Cb - 128;

                float rVal = Y + (1370705 * Cr_128 / 1000000);
                float gVal = Y - (337633 * Cb_128 / 1000000) - (698001 * Cr_128 / 1000000);
                float bVal = Y + (1732446 * Cb_128 / 1000000);

                /*
                // Formula used in JPEG standard. Gives pretty similar results
                //int rVal = Y + (1402000 * Cr_128/ 1000000);
                //int gVal = Y - (344140 * Cb_128 / 1000000) - (714140 * Cr_128 / 1000000);
                //int bVal = Y + (1772000 * Cb_128 / 1000000);
                */

                byte rByte = (rVal < 0) ? (byte) 0 : (rVal > 255) ? (byte) 0xFF : (byte) rVal;
                byte gByte = (gVal < 0) ? (byte) 0 : (gVal > 255) ? (byte) 0xFF : (byte) gVal;
                byte bByte = (bVal < 0) ? (byte) 0 : (bVal > 255) ? (byte) 0xFF : (byte) bVal;
                float alpha = K;
                if (y < smaskHeight && x < smaskWidth && smaskRaster != null)
                    alpha = (smaskRaster.getSample(x, y, 0) & 0xFF);
                else if (y < maskHeight && x < maskWidth && maskRaster != null) {
                    // When making an ImageMask, the alpha channnel is setup so that
                    //  it both works correctly for the ImageMask being painted,
                    //  and also for when it's used here, to determine the alpha
                    //  of an image that it's masking
                    alpha = (maskImage.getRGB(x, y) >>> 24) & 0xFF; // Extract Alpha from ARGB
                }

                rgbaValues[0] = rByte;
                rgbaValues[1] = gByte;
                rgbaValues[2] = bByte;
                rgbaValues[3] = (int)alpha;

                wr.setPixel(x, y, rgbaValues);
            }
        }
    }

    /**
     * Explicit Masking algorithm, as of PDF 1.3.  The entry in an image dictionary
     * may be an image mask, as described under "Stencil Masking", which serves as
     * an explicit mask for the primary or base image.  The base image and the
     * image mask need not have the same resolution (width, height), but since
     * all images are defined on the unit square in user space, their boundaries on the
     * page will conincide; that is, they will overlay each other.
     * <p/>
     * The image mask indicates indicates which places on the page are to be painted
     * and which are to be masked out (left unchanged).  Unmasked areas are painted
     * with the corresponding portions of the base image; masked areas are not.
     *
     * @param baseImage base image in which the mask weill be applied to
     * @param maskImage image mask to be applied to base image.
     */
    private static void applyExplicitMask(BufferedImage baseImage, BufferedImage maskImage) {
        // check to see if we need to scale the mask to match the size of the
        // base image.
        int baseWidth = baseImage.getWidth();
        int baseHeight = baseImage.getHeight();
        int maskWidth = maskImage.getWidth();
        int maskHeight = maskImage.getHeight();

        if (baseWidth != maskWidth || baseHeight != maskHeight) {
            // calculate scale factors.
            double scaleX = baseWidth / (double) maskWidth;
            double scaleY = baseHeight / (double) maskHeight;
            // scale the mask to match the base image.
            AffineTransform tx = new AffineTransform();
            tx.scale(scaleX, scaleY);
            AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
            BufferedImage sbim = op.filter(maskImage, null);
            maskImage.flush();
            maskImage = sbim;
        }
        // apply the mask by simply painting white to the base image where
        // the mask specified no colour.
        for (int y = 0; y < baseHeight; y++) {
            for (int x = 0; x < baseWidth; x++) {
                int maskPixel = maskImage.getRGB(x, y);
                if (maskPixel == -1 || maskPixel == 0xffffff) {
                    baseImage.setRGB(x, y, Color.WHITE.getRGB());
                }
            }
        }

//        int width = maskImage.getWidth();
//        int height = maskImage.getHeight();
//        final BufferedImage bi = new BufferedImage(width,height, BufferedImage.TYPE_INT_RGB);
//        for (int y = 0; y < height; y++) {
//            for (int x = 0; x < width; x++) {
//                if (maskImage.getRGB(x,y) != -1){
//                    bi.setRGB(x, y, Color.red.getRGB());
//                }
//                else{
//                    bi.setRGB(x, y, Color.green.getRGB());
//                }
//            }
//        }
//        final JFrame f = new JFrame("Test");
//        f.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
//
//        JComponent image = new JComponent() {
//            @Override
//            public void paint(Graphics g_) {
//                super.paint(g_);
//                g_.drawImage(bi, 0, 0, f);
//            }
//        };
//        image.setPreferredSize(new Dimension(bi.getWidth(), bi.getHeight()));
//        image.setSize(new Dimension(bi.getWidth(), bi.getHeight()));
//
//        JPanel test  =new JPanel();
//        test.setPreferredSize(new Dimension(1200,1200));
//        JScrollPane tmp = new JScrollPane(image);
//        tmp.revalidate();
//        f.setSize(new Dimension(800, 800));
//        f.getContentPane().add(tmp);
//        f.validate();
//        f.setVisible(true);

    }

    private static BufferedImage alterBufferedImage(BufferedImage bi, BufferedImage smaskImage, BufferedImage maskImage, int[] maskMinRGB, int[] maskMaxRGB) {
        Raster smaskRaster = null;
        int smaskWidth = 0;
        int smaskHeight = 0;

        int width = bi.getWidth();
        int height = bi.getHeight();

        if (smaskImage != null) {
            smaskRaster = smaskImage.getRaster();
            smaskWidth = smaskRaster.getWidth();
            smaskHeight = smaskRaster.getHeight();
            // scale the image to match the image mask.
            if (width < smaskWidth || height < smaskHeight) {
                // calculate scale factors.
                double scaleX = smaskWidth / (double) width;
                double scaleY = smaskHeight / (double) height;
                // scale the mask to match the base image.
                AffineTransform tx = new AffineTransform();
                tx.scale(scaleX, scaleY);
                AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
                BufferedImage bim = op.filter(bi, null);
                bi.flush();
                bi = bim;
            }
            width = bi.getWidth();
            height = bi.getHeight();
        }

        Raster maskRaster = null;
        int maskWidth = 0;
        int maskHeight = 0;
        if (maskImage != null) {
            maskRaster = maskImage.getRaster();
            maskWidth = maskRaster.getWidth();
            maskHeight = maskRaster.getHeight();
        }

        int maskMinRed = 0xFF;
        int maskMinGreen = 0xFF;
        int maskMinBlue = 0xFF;
        int maskMaxRed = 0x00;
        int maskMaxGreen = 0x00;
        int maskMaxBlue = 0x00;
        if (maskMinRGB != null && maskMaxRGB != null) {
            maskMinRed = maskMinRGB[0];
            maskMinGreen = maskMinRGB[1];
            maskMinBlue = maskMinRGB[2];
            maskMaxRed = maskMaxRGB[0];
            maskMaxGreen = maskMaxRGB[1];
            maskMaxBlue = maskMaxRGB[2];
        }

        if (smaskRaster == null && maskRaster == null &&
                (maskMinRGB == null || maskMaxRGB == null)){
            return null;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean gotARBG = false;
                int argb = 0;
                int alpha = 0xFF;
                if (y < smaskHeight && x < smaskWidth && smaskRaster != null) {
                    // Alpha equals greyscale value of smask
                    alpha = (smaskRaster.getSample(x, y, 0) & 0xFF);
                } else if (y < maskHeight && x < maskWidth && maskRaster != null) {
                    // When making an ImageMask, the alpha channnel is setup so that
                    //  it both works correctly for the ImageMask being painted,
                    //  and also for when it's used here, to determine the alpha
                    //  of an image that it's masking
                    alpha = (maskImage.getRGB(x, y) >>> 24) & 0xFF; // Extract Alpha from ARGB
                } else {
                    gotARBG = true;
                    argb = bi.getRGB(x, y);
                    int red = ((argb >> 16) & 0xFF);
                    int green = ((argb >> 8) & 0xFF);
                    int blue = (argb & 0xFF);
                    if (blue >= maskMinBlue && blue <= maskMaxBlue &&
                            green >= maskMinGreen && green <= maskMaxGreen &&
                            red >= maskMinRed && red <= maskMaxRed) {
                        alpha = 0x00;
                    }
                }
                if (alpha != 0xFF) {
                    if (!gotARBG)
                        argb = bi.getRGB(x, y);
                    argb &= 0x00FFFFFF;
                    argb |= ((alpha << 24) & 0xFF000000);
                    bi.setRGB(x, y, argb);
                }
            }
        }
        // apply the soft mask.
        BufferedImage tmpImage = bi;
        if (smaskImage != null) {
            BufferedImage argbImage = new BufferedImage(width,
                    height, BufferedImage.TYPE_INT_ARGB);
            int[] srcBand = new int[width];
            int[] sMaskBand = new int[width];
            // iterate over each band to apply the mask
            for (int i = 0; i < height; i++) {
                tmpImage.getRGB(0, i, width, 1, srcBand, 0, width);
                smaskImage.getRGB(0, i, width, 1, sMaskBand, 0, width);
                // apply the soft mask blending
                for (int j = 0; j < width; j++) {
                    sMaskBand[j] = ((sMaskBand[j] & 0xff) << 24)
                            | (srcBand[j] & ~0xff000000);
                }
                argbImage.setRGB(0, i, width, 1, sMaskBand, 0, width);
            }
            tmpImage.flush();
            tmpImage = argbImage;
        }
        return tmpImage;
    }

    private static WritableRaster alterRasterRGBA(WritableRaster wr, BufferedImage smaskImage, BufferedImage maskImage, int[] maskMinRGB, int[] maskMaxRGB) {
        Raster smaskRaster = null;
        int smaskWidth = 0;
        int smaskHeight = 0;
        if (smaskImage != null) {
            smaskRaster = smaskImage.getRaster();
            smaskWidth = smaskRaster.getWidth();
            smaskHeight = smaskRaster.getHeight();
        }

        Raster maskRaster = null;
        int maskWidth = 0;
        int maskHeight = 0;
        if (maskImage != null) {
            maskRaster = maskImage.getRaster();
            maskWidth = maskRaster.getWidth();
            maskHeight = maskRaster.getHeight();
        }

        int maskMinRed = 0xFF;
        int maskMinGreen = 0xFF;
        int maskMinBlue = 0xFF;
        int maskMaxRed = 0x00;
        int maskMaxGreen = 0x00;
        int maskMaxBlue = 0x00;
        if (maskMinRGB != null && maskMaxRGB != null) {
            maskMinRed = maskMinRGB[0];
            maskMinGreen = maskMinRGB[1];
            maskMinBlue = maskMinRGB[2];
            maskMaxRed = maskMaxRGB[0];
            maskMaxGreen = maskMaxRGB[1];
            maskMaxBlue = maskMaxRGB[2];
        }

        if (smaskRaster == null && maskRaster == null && (maskMinRGB == null || maskMaxRGB == null))
            return null;

        int[] rgbaValues = new int[4];
        int width = wr.getWidth();
        int height = wr.getHeight();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    wr.getPixel(x, y, rgbaValues);
                    int red = rgbaValues[0];
                    int green = rgbaValues[1];
                    int blue = rgbaValues[2];
    
                    int alpha = 0xFF;
                    if (y < smaskHeight && x < smaskWidth && smaskRaster != null) {
                        // Alpha equals greyscale value of smask
                        alpha = (smaskImage.getRGB(x, y)) & 0xFF;//(smaskRaster.getSample(x, y, 0) & 0xFF);
                    } else if (y < maskHeight && x < maskWidth && maskRaster != null) {
                        // When making an ImageMask, the alpha channnel is setup so that
                        //  it both works correctly for the ImageMask being painted,
                        //  and also for when it's used here, to determine the alpha
                        //  of an image that it's masking
                        alpha = (maskImage.getRGB(x, y) >>> 24) & 0xFF; // Extract Alpha from ARGB
                    } else if (blue >= maskMinBlue && blue <= maskMaxBlue &&
                            green >= maskMinGreen && green <= maskMaxGreen &&
                            red >= maskMinRed && red <= maskMaxRed) {
                        alpha = 0x00;
                    }
                    if (alpha != 0xFF) {
                        rgbaValues[3] = alpha;
                        wr.setPixel(x, y, rgbaValues);
                    }
                }
        }
        return wr;
    }

    private static void alterRasterRGB2PColorSpace(WritableRaster wr, PColorSpace colorSpace) {
//System.out.println("alterRasterRGB2PColorSpace() colorSpace: " + colorSpace);
        if (colorSpace instanceof DeviceRGB)
            return;
        float[] values = new float[3];
        int[] rgbValues = new int[3];
        int width = wr.getWidth();
        int height = wr.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, rgbValues);

                PColorSpace.reverseInPlace(rgbValues);
                colorSpace.normaliseComponentsToFloats(rgbValues, values, 255.0f);
                Color c = colorSpace.getColor(values);
                rgbValues[0] = c.getRed();
                rgbValues[1] = c.getGreen();
                rgbValues[2] = c.getBlue();

                wr.setPixel(x, y, rgbValues);
            }
        }
    }

    private static void alterRasterY2Gray(WritableRaster wr, int bitsPerComponent,
                                          Vector decode) {
        int[] values = new int[1];
        int width = wr.getWidth();
        int height = wr.getHeight();
        boolean defaultDecode = (0.0f == ((Number) decode.elementAt(0)).floatValue());

        int Y;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                wr.getPixel(x, y, values);
                Y = values[0];
                Y = defaultDecode?255 - Y:Y;
                Y = (Y < 0) ? (byte) 0 : (Y > 255) ? (byte) 0xFF : (byte) Y;
                values[0] = Y;
                wr.setPixel(x, y, values);
            }
        }
    }

    private static int getJPEGEncoding(byte[] data, int dataLength) {
        int jpegEncoding = JPEG_ENC_UNKNOWN_PROBABLY_YCbCr;

        boolean foundAPP14 = false;
        byte compsTypeFromAPP14 = 0;
        boolean foundSOF = false;
        int numCompsFromSOF = 0;
        boolean foundSOS = false;
        int numCompsFromSOS = 0;

        int index = 0;
        while (true) {
            if (index >= dataLength)
                break;
            if (data[index] != ((byte) 0xFF))
                break;
            if (foundAPP14 && foundSOF)
                break;
            byte segmentType = data[index + 1];
            index += 2;
            if (segmentType == ((byte) 0xD8)) {
                //System.out.println("Found SOI (0xD8)");
                continue;
            }

            //System.out.println("Segment: " + Integer.toHexString( ((int)segmentType)&0xFF ));
            int length = (((data[index] << 8)) & 0xFF00) + (((int) data[index + 1]) & 0xFF);
            //System.out.println("   Length: " + length + "    Index: " + index);

            // APP14 (Might be Adobe file)
            if (segmentType == ((byte) 0xEE)) {
                //System.out.println("Found APP14 (0xEE)");
                if (length >= 14) {
                    foundAPP14 = true;
                    compsTypeFromAPP14 = data[index + 13];
                    //System.out.println("APP14 format: " + compsTypeFromAPP14);
                }
            } else if (segmentType == ((byte) 0xC0)) {
                foundSOF = true;
                //System.out.println("Found SOF (0xC0)  Start Of Frame");
                //int bitsPerSample = ( ((int)data[index+2]) & 0xFF );
                //int imageHeight = ( ((int)(data[index+3] << 8)) & 0xFF00 ) + ( ((int)data[index+4]) & 0xFF );
                //int imageWidth = ( ((int)(data[index+5] << 8)) & 0xFF00 ) + ( ((int)data[index+6]) & 0xFF );
                numCompsFromSOF = (((int) data[index + 7]) & 0xFF);
                //System.out.println("   bitsPerSample: " + bitsPerSample + ", imageWidth: " + imageWidth + ", imageHeight: " + imageHeight + ", numComps: " + numCompsFromSOF);
                //int[] compIds = new int[numCompsFromSOF];
                //for(int i = 0; i < numCompsFromSOF; i++) {
                //    compIds[i] = ( ((int)data[index+8+(i*3)]) & 0xff );
                //    System.out.println("    compId: " + compIds[i]);
                //}
            } else if (segmentType == ((byte) 0xDA)) {
                foundSOS = true;
                //System.out.println("Found SOS (0xDA)  Start Of Scan");
                numCompsFromSOS = (((int) data[index + 2]) & 0xFF);
                //int[] compIds = new int[numCompsFromSOS];
                //for(int i = 0; i < numCompsFromSOS; i++) {
                //    compIds[i] = ( ((int)data[index+3+(i*2)]) & 0xff );
                //    System.out.println("    compId: " + compIds[i]);
                //}
            }

            //System.out.println("   Data: " + org.icepdf.core.util.Utils.convertByteArrayToHexString( data, index+2, Math.min(length-2,dataLength-index-2), true, 20, '\n' ));
            index += length;
        }

        if (foundAPP14 && foundSOF) {
            if (compsTypeFromAPP14 == 0) {       // 0 seems to indicate no conversion
                if (numCompsFromSOF == 1)
                    jpegEncoding = JPEG_ENC_GRAY;
                if (numCompsFromSOF == 3)        // Most assume RGB. DesignJava_times_roman_substitution.PDF supports this.
                    jpegEncoding = JPEG_ENC_RGB;
                else if (numCompsFromSOF == 4)   // CMYK
                    jpegEncoding = JPEG_ENC_CMYK;
            } else if (compsTypeFromAPP14 == 1) {  // YCbCr
                jpegEncoding = JPEG_ENC_YCbCr;
            } else if (compsTypeFromAPP14 == 2) {  // YCCK
                jpegEncoding = JPEG_ENC_YCCK;
            }
        } else if (foundSOS) {
            if (numCompsFromSOS == 1)
                jpegEncoding = JPEG_ENC_GRAY; // Y
        }
        return jpegEncoding;
    }

    /**
     * Utility to build an RGBA buffered image using the specified raster and
     * a Transparency.OPAQUE transparency model.
     *
     * @param wr           writable raster of image.
     * @return constructed image.
     */
    private static BufferedImage makeRGBABufferedImage(WritableRaster wr) {
        return makeRGBABufferedImage(wr, Transparency.OPAQUE);
    }

    /**
     * Utility to build an RGBA buffered image using the specified raster and
     * transparency type.
     *
     * @param wr           writable raster of image.
     * @param transparency any valid Transparency interface type. Bitmask,
     *                     opaque and translucent.
     * @return constructed image.
     */
    private static BufferedImage makeRGBABufferedImage(WritableRaster wr,
                                                       final int transparency) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int[] bits = new int[4];
        for (int i = 0; i < bits.length; i++)
            bits[i] = 8;
        ColorModel cm = new ComponentColorModel(
                cs, bits, true, false,
                transparency,
                wr.getTransferType());
        BufferedImage img = new BufferedImage(cm, wr, false, null);
        return img;
    }

    private static BufferedImage makeRGBBufferedImage(WritableRaster wr) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        int[] bits = new int[3];
        for (int i = 0; i < bits.length; i++)
            bits[i] = 8;
        ColorModel cm = new ComponentColorModel(
                cs, bits, false, false,
                ColorModel.OPAQUE,
                wr.getTransferType());
        BufferedImage img = new BufferedImage(cm, wr, false, null);
        return img;
    }

    private static BufferedImage makeGrayBufferedImage(WritableRaster wr) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        int[] bits = new int[1];
        for (int i = 0; i < bits.length; i++)
            bits[i] = 8;
        ColorModel cm = new ComponentColorModel(
                cs, bits, false, false,
                ColorModel.OPAQUE,
                wr.getTransferType());
        BufferedImage img = new BufferedImage(cm, wr, false, null);
        return img;
    }

    // This method returns a buffered image with the contents of an image from
    // java almanac

    private BufferedImage makeRGBABufferedImageFromImage(Image image) {

        if (image instanceof BufferedImage) {
            return (BufferedImage) image;
        }
        // This code ensures that all the pixels in the image are loaded
        image = new ImageIcon(image).getImage();
        // Determine if the image has transparent pixels; for this method's
        // implementation, see Determining If an Image Has Transparent Pixels
        boolean hasAlpha = hasAlpha(image);
        // Create a buffered image with a format that's compatible with the screen
        BufferedImage bImage = null;
        try {
            // graphics environment calls can through headless exceptions so
            // proceed with caution.
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gs = ge.getDefaultScreenDevice();
            GraphicsConfiguration gc = gs.getDefaultConfiguration();
            // Determine the type of transparency of the new buffered image
            int transparency = Transparency.OPAQUE;
            if (hasAlpha) {
                transparency = Transparency.BITMASK;
            }
            // Create the buffered image
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            if (width == -1 || height == -1) {
                return null;
            }
            bImage = gc.createCompatibleImage(
                    image.getWidth(null), image.getHeight(null), transparency);
        } catch (HeadlessException e) {
            // The system does not have a screen
        }
        if (bImage == null) {
            // Create a buffered image using the default color model
            int type = BufferedImage.TYPE_INT_RGB;
            if (hasAlpha) {
                type = BufferedImage.TYPE_INT_ARGB;
            }
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            if (width == -1 || height == -1) {
                return null;
            }
            bImage = new BufferedImage(width, height, type);
        }
        // Copy image to buffered image
        Graphics g = bImage.createGraphics();
        // Paint the image onto the buffered image
        g.drawImage(image, 0, 0, null);
        g.dispose();
        image.flush();
        return bImage;
    }

    // returns true if the specified image has transparent pixels, from
    // java almanac

    private static boolean hasAlpha(Image image) {
        // If buffered image, the color model is readily available
        if (image instanceof BufferedImage) {
            BufferedImage bufferedImage = (BufferedImage) image;
            return bufferedImage.getColorModel().hasAlpha();
        }
        // Use a pixel grabber to retrieve the image's color model;
        // grabbing a single pixel is usually sufficient
        PixelGrabber pixelGrabber = new PixelGrabber(image, 0, 0, 1, 1, false);
        try {
            pixelGrabber.grabPixels();
        } catch (InterruptedException e) {
            // fail quietly
        }
        // Get the image's color model
        ColorModel cm = pixelGrabber.getColorModel();
        return cm == null || cm.hasAlpha();
    }

    /**
     * CCITT fax decode algorithm, decodes the stream into a valid image
     * stream that can be used to create a BufferedImage.
     *
     * @param width  of image
     * @param height height of image.
     * @return decoded stream bytes.
     */
    private byte[] ccittFaxDecode(int width, int height) {

        byte[] streamData = getDecodedStreamBytes();
        Hashtable decodeParms = library.getDictionary(entries, "DecodeParms");
        float k = library.getFloat(decodeParms, "K");
        // default value is always false
        boolean blackIs1 = getBlackIs1(library, decodeParms);
        // get value of key if it is available.
        boolean encodedByteAlign = false;
        Object encodedByteAlignObject = library.getObject(decodeParms, "EncodedByteAlign");
        if (encodedByteAlignObject instanceof Boolean) {
            encodedByteAlign = (Boolean) encodedByteAlignObject;
        }
        int columns = library.getInt(decodeParms, "Columns");
        int rows = library.getInt(decodeParms, "Rows");

        if (columns == 0) {
            columns = width;
        }
        if (rows == 0) {
            rows = height;
        }
        int size = rows * ((columns + 7) >> 3);
        byte[] decodedStreamData = new byte[size];
        CCITTFaxDecoder decoder = new CCITTFaxDecoder(1, columns, rows);
        decoder.setAlign(encodedByteAlign);
        // pick three three possible fax encoding.
        try {
            if (k == 0) {
                decoder.decodeT41D(decodedStreamData, streamData, 0, rows);
            } else if (k > 0) {
                decoder.decodeT42D(decodedStreamData, streamData, 0, rows);
            } else if (k < 0) {
                decoder.decodeT6(decodedStreamData, streamData, 0, rows);
            }
        } catch (Exception e) {
            logger.warning("Error decoding CCITTFax image k: " + k );
            // IText 5.03 doesn't correctly assign a k value for the deocde,
            // as  result we can try one more time using the T6.
            decoder.decodeT6(decodedStreamData, streamData, 0, rows);
        }
        // check the black is value flag, no one likes inverted colours.
        if (!blackIs1) {
            // toggle the byte data invert colour, not bit operand.
            for (int i = 0; i < decodedStreamData.length; i++) {
                decodedStreamData[i] = (byte) ~decodedStreamData[i];
            }
        }
        return decodedStreamData;
    }

    /**
     * Gets the decoded Byte stream of the Stream object.
     *
     * @return decoded Byte stream
     */
    public byte[] getBytes() {
        byte[] data = getDecodedStreamBytes();
        if (data == null)
            data = new byte[0];
        return data;
    }

    /**
     * Gets the image object for the given resource.  This method can optionally
     * scale an image to reduce the total memory foot print or to increase the
     * perceived render quality on screen at low zoom levels.
     *
     * @param fill         color value of image
     * @param resources    resouces containing image reference
     * @param allowScaling true indicates that the image will be scaled, fals
     *                     no scaling.
     * @return new image object
     */
    // was synchronized, not think it is needed?
    public BufferedImage getImage(Color fill, Resources resources, boolean allowScaling) {
        if (Tagger.tagging)
            Tagger.beginImage(pObjectReference, inlineImage);
        //String debugFill = (fill == null) ? "null" : Integer.toHexString(fill.getRGB());
        //System.out.println("Stream.getImage()  for: " + pObjectReference + "  fill: " + debugFill + "\n  stream: " + this);

        if (Tagger.tagging)
            Tagger.tagImage("Filter=" + getNormalisedFilterNames());

        // parse colour space
        PColorSpace colourSpace = null;
        Object o = library.getObject(entries, "ColorSpace");
        if (resources != null) {
            colourSpace = resources.getColorSpace(o);
        }
        //colorSpace = PColorSpace.getColorSpace(library, o);
        // assume b&w image is no colour space
        if (colourSpace == null) {
            colourSpace = new DeviceGray(library, null);
            if (Tagger.tagging)
                Tagger.tagImage("ColorSpace_Implicit_DeviceGray");
        }
        if (Tagger.tagging)
            Tagger.tagImage("ColorSpace=" + colourSpace.getDescription());

        boolean imageMask = isImageMask();
        if (Tagger.tagging)
            Tagger.tagImage("ImageMask=" + imageMask);
        if (imageMask)
            allowScaling = false;

        // find out the number of bits in the image
        int bitspercomponent = library.getInt(entries, "BitsPerComponent");
        if (imageMask && bitspercomponent == 0) {
            bitspercomponent = 1;
            if (Tagger.tagging)
                Tagger.tagImage("BitsPerComponent_Implicit_1");
        }
        if (Tagger.tagging)
            Tagger.tagImage("BitsPerComponent=" + bitspercomponent);

        // get dimension of image stream
        int width = library.getInt(entries, "Width");
        int height = library.getInt(entries, "Height");

        // check for available memory, get colour space and bit count
        // to better estimate size of image in memory
        int colorSpaceCompCount = colourSpace.getNumComponents();

        // parse decode information
        Vector<Integer> decode = (Vector) library.getObject(entries, "Decode");
        if (decode == null) {
            int depth = colourSpace.getNumComponents();
            decode = new Vector<Integer>(depth);
            // add a decode param for each colour channel.
            for (int i= 0; i < depth; i++){
                decode.addElement(0);
                decode.addElement(1);
            }
            if (Tagger.tagging)
                Tagger.tagImage("Decode_Implicit_01");
        }
        if (Tagger.tagging)
            Tagger.tagImage("Decode=" + decode);

        BufferedImage smaskImage = null;
        BufferedImage maskImage = null;
        int[] maskMinRGB = null;
        int[] maskMaxRGB = null;
        int maskMinIndex = -1;
        int maskMaxIndex = -1;
        Object smaskObj = library.getObject(entries, "SMask");
        Object maskObj = library.getObject(entries, "Mask");
        if (smaskObj instanceof Stream) {
            if (Tagger.tagging)
                Tagger.tagImage("SMaskStream");
            Stream smaskStream = (Stream) smaskObj;
            if (smaskStream.isImageSubtype())
                smaskImage = smaskStream.getImage(fill, resources, false);
        }
        if (smaskImage != null) {
            if (Tagger.tagging)
                Tagger.tagImage("SMaskImage");
            allowScaling = false;
        }
        if (maskObj != null && smaskImage == null) {
            if (maskObj instanceof Stream) {
                if (Tagger.tagging)
                    Tagger.tagImage("MaskStream");
                Stream maskStream = (Stream) maskObj;
                if (maskStream.isImageSubtype()) {
                    maskImage = maskStream.getImage(fill, resources, false);
                    if (maskImage != null) {
                        if (Tagger.tagging)
                            Tagger.tagImage("MaskImage");
                    }
                }
            } else if (maskObj instanceof Vector) {
                if (Tagger.tagging)
                    Tagger.tagImage("MaskVector");
                Vector maskVector = (Vector) maskObj;
                int[] maskMinOrigCompsInt = new int[colorSpaceCompCount];
                int[] maskMaxOrigCompsInt = new int[colorSpaceCompCount];
                for (int i = 0; i < colorSpaceCompCount; i++) {
                    if ((i * 2) < maskVector.size())
                        maskMinOrigCompsInt[i] = ((Number) maskVector.get(i * 2)).intValue();
                    if ((i * 2 + 1) < maskVector.size())
                        maskMaxOrigCompsInt[i] = ((Number) maskVector.get(i * 2 + 1)).intValue();
                }
                if (colourSpace instanceof Indexed) {
                    Indexed icolourSpace = (Indexed) colourSpace;
                    Color[] colors = icolourSpace.accessColorTable();
                    if (colors != null &&
                            maskMinOrigCompsInt.length >= 1 &&
                            maskMaxOrigCompsInt.length >= 1) {
                        maskMinIndex = maskMinOrigCompsInt[0];
                        maskMaxIndex = maskMaxOrigCompsInt[0];
                        if (maskMinIndex >= 0 && maskMinIndex < colors.length &&
                                maskMaxIndex >= 0 && maskMaxIndex < colors.length) {
                            Color minColor = colors[maskMinOrigCompsInt[0]];
                            Color maxColor = colors[maskMaxOrigCompsInt[0]];
                            maskMinRGB = new int[]{minColor.getRed(), minColor.getGreen(), minColor.getBlue()};
                            maskMaxRGB = new int[]{maxColor.getRed(), maxColor.getGreen(), maxColor.getBlue()};
                        }
                    }
                } else {
                    PColorSpace.reverseInPlace(maskMinOrigCompsInt);
                    PColorSpace.reverseInPlace(maskMaxOrigCompsInt);
                    float[] maskMinOrigComps = new float[colorSpaceCompCount];
                    float[] maskMaxOrigComps = new float[colorSpaceCompCount];
                    colourSpace.normaliseComponentsToFloats(maskMinOrigCompsInt, maskMinOrigComps, (1 << bitspercomponent) - 1);
                    colourSpace.normaliseComponentsToFloats(maskMaxOrigCompsInt, maskMaxOrigComps, (1 << bitspercomponent) - 1);

                    Color minColor = colourSpace.getColor(maskMinOrigComps);
                    Color maxColor = colourSpace.getColor(maskMaxOrigComps);
                    PColorSpace.reverseInPlace(maskMinOrigComps);
                    PColorSpace.reverseInPlace(maskMaxOrigComps);
                    maskMinRGB = new int[]{minColor.getRed(), minColor.getGreen(), minColor.getBlue()};
                    maskMaxRGB = new int[]{maxColor.getRed(), maxColor.getGreen(), maxColor.getBlue()};
                }
            }
        }

        BufferedImage img = getImage(
                colourSpace, fill,
                width, height,
                colorSpaceCompCount,
                bitspercomponent,
                imageMask,
                decode,
                smaskImage,
                maskImage,
                maskMinRGB, maskMaxRGB,
                maskMinIndex, maskMaxIndex);
        if (img != null) {
            img = putIntoImageCache(img, width, height, allowScaling);
        }
//String title = "Image: " + getPObjectReference();
//CCITTFax.showRenderedImage(img, title);
        if (Tagger.tagging)
            Tagger.endImage(pObjectReference);
        return img;
    }

    /**
     * Utility to to the image work, the public version pretty much just
     * parses out image dictionary parameters.  This method start the actual
     * image decoding.
     *
     * @param colourSpace         colour space of image.
     * @param fill                fill color to aply to image from current graphics context.
     * @param width               width of image.
     * @param height              heigth of image
     * @param colorSpaceCompCount colour space component count, 1, 3, 4 etc.
     * @param bitsPerComponent    number of bits that represent one component.
     * @param imageMask           boolean flag to use image mask or not.
     * @param decode              decode array, 1,0 or 0,1 can effect colour interpretation.
     * @param smaskImage          smaask image value, optional.
     * @param maskImage           buffered image image mask to apply to decoded image, optional.
     * @param maskMinRGB          max rgb values for the mask
     * @param maskMaxRGB          min rgb values for the mask.
     * @param maskMinIndex        max indexed colour values for the mask.
     * @param maskMaxIndex        min indexed colour values for the mask.
     * @return buffered image of decoded image stream, null if an error occured.
     */
    private BufferedImage getImage(
            PColorSpace colourSpace, Color fill,
            int width, int height,
            int colorSpaceCompCount,
            int bitsPerComponent,
            boolean imageMask,
            Vector decode,
            BufferedImage smaskImage,
            BufferedImage maskImage,
            int[] maskMinRGB, int[] maskMaxRGB,
            int maskMinIndex, int maskMaxIndex) {

        // return cached image if we have already.
        if (image != null) {
            // If Stream.dispose(true) was called since last call to Stream.getPageImage(),
            //   then might have to read from file
            checkMemory(width * height * Math.max(colorSpaceCompCount, 4));
            //TODO There's a race-ish condition here
            //  checkMemory() could have called Page.reduceMemory(), which
            //  could have called Stream.dispose(), which could have nulled
            //  out our "image" field
            BufferedImage img = null;
            synchronized (imageLock) {
                if (image != null) {
                    img = image.readImage();
                }
            }
            if (img != null) {
                return img;
            }
        }

        // No image cash yet, so we decode known image types.
        if (image == null) {
            BufferedImage decodedImage = null;

            // JPEG writes out image if successful
            if (shouldUseDCTDecode()) {
                if (Tagger.tagging)
                    Tagger.tagImage("DCTDecode");
                decodedImage = dctDecode(width, height, colourSpace, bitsPerComponent,
                        smaskImage, maskImage, maskMinRGB, maskMaxRGB, decode);
            }
            // JBIG2 writes out image if successful
            else if (shouldUseJBIG2Decode()) {
                if (Tagger.tagging)
                    Tagger.tagImage("JBIG2Decode");
                decodedImage = jbig2Decode(width, height, fill);
            }
            // JPEG2000 writes out image if successful
            else if (shouldUseJPXDecode()) {
                if (Tagger.tagging)
                    Tagger.tagImage("JPXDecode");
                decodedImage = jpxDecode(width, height, colourSpace, bitsPerComponent, fill,
                        smaskImage, maskImage, maskMinRGB, maskMaxRGB, decode);
            }
            // CCITTFax load via JAI, if this fails we have other non JAI
            // CCITTFax code to fall back on.
            else if (shouldUseCCITTFaxDecode()){
                 if (Tagger.tagging)
                    Tagger.tagImage("CCITTFaxDecode JAI");
                decodedImage =
                    CCITTFax.attemptDeriveBufferedImageFromBytes(this, library,
                            entries, fill);
            }

            // finally if we have something then we return it.
            if (decodedImage != null) {
                // write tmpImage to the cache
                return decodedImage;
            }
        }

        byte[] data;
        int dataLength;
        // CCITTfax data is raw byte decode.
        if (shouldUseCCITTFaxDecode()) {
            if (Tagger.tagging)
                Tagger.tagImage("CCITTFaxDecode");
            data = ccittFaxDecode(width, height);
            dataLength = data.length;
        }
        // no further processing needed we can just get the stream bytes using
        // normal filter decompression.
        else {
            Object[] dataAndSize = getDecodedStreamBytesAndSize(
                    width * height
                            * colourSpace.getNumComponents()
                            * bitsPerComponent / 8);
            data = (byte[]) dataAndSize[0];
            dataLength = (Integer) dataAndSize[1];
        }
        // finally push the bytes though the common image processor
        if (data != null) {
            try {
                BufferedImage decodedImage = makeImageWithRasterFromBytes(
                        colourSpace, fill,
                        width, height,
                        colorSpaceCompCount,
                        bitsPerComponent,
                        imageMask,
                        decode,
                        smaskImage,
                        maskImage,
                        maskMinRGB, maskMaxRGB,
                        maskMinIndex, maskMaxIndex,
                        data, dataLength);
                // if we have something then we can decode it.
                if (decodedImage != null) {
                    return decodedImage;
                }
            }
            catch (Exception e) {
                logger.log(Level.FINE, "Error building image raster.", e);
            }
        }

        // decodes the image stream and returns an image object. Legacy fallback
        // code, should never get here, put there are always corner cases. .
        BufferedImage decodedImage = parseImage(
                width,
                height,
                colourSpace,
                imageMask,
                fill,
                bitsPerComponent,
                decode,
                data,
                smaskImage,
                maskImage,
                maskMinRGB, maskMaxRGB);
        return decodedImage;
    }

    private BufferedImage makeImageWithRasterFromBytes(
            PColorSpace colourSpace,
            Color fill,
            int width, int height,
            int colorSpaceCompCount,
            int bitspercomponent,
            boolean imageMask,
            Vector decode,
            BufferedImage smaskImage,
            BufferedImage maskImage,
            int[] maskMinRGB, int[] maskMaxRGB,
            int maskMinIndex, int maskMaxIndex, byte[] data, int dataLength) {
        BufferedImage img = null;

        // check if the ICCBased colour has an alternative that
        // we might support for decoding with a colorModel.
        if (colourSpace instanceof ICCBased) {
            ICCBased iccBased = (ICCBased) colourSpace;
            if (iccBased.getAlternate() != null) {
                // set the alternate as the current
                colourSpace = iccBased.getAlternate();
            }
        }

        if (colourSpace instanceof DeviceGray) {
            //System.out.println("Stream.makeImageWithRasterFromBytes()  DeviceGray");
            if (imageMask && bitspercomponent == 1) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_DeviceGray_1_ImageMask");
                //byte[] data = getDecodedStreamBytes();
                //int data_length = data.length;
                DataBuffer db = new DataBufferByte(data, dataLength);
                WritableRaster wr = Raster.createPackedRaster(db, width, height,
                        bitspercomponent, new Point(0, 0));

                // From PDF 1.6 spec, concerning ImageMask and Decode array:
                // todo we nee dot apply the decode method generically as we do
                // it in different places different ways.
                // [0 1] (the default for an image mask), a sample value of 0 marks
                //       the page with the current color, and a 1 leaves the previous
                //       contents unchanged.
                // [1 0] Is the reverse
                // In case alpha transparency doesn't work, it'll paint white opaquely
                boolean defaultDecode = (0.0f == ((Number) decode.elementAt(0)).floatValue());
                //int a = Color.white.getRGB();
                int a = 0x00FFFFFF; // Clear if alpha supported, else white
                int[] cmap = new int[]{
                        (defaultDecode ? fill.getRGB() : a),
                        (defaultDecode ? a : fill.getRGB())
                };
                int transparentIndex = (defaultDecode ? 1 : 0);
                IndexColorModel icm = new IndexColorModel(
                        bitspercomponent,       // the number of bits each pixel occupies
                        cmap.length,            // the size of the color component arrays
                        cmap,                   // the array of color components
                        0,                      // the starting offset of the first color component
                        true,                   // indicates whether alpha values are contained in the cmap array
                        transparentIndex,       // the index of the fully transparent pixel
                        db.getDataType());      // the data type of the array used to represent pixel values. The data type must be either DataBuffer.TYPE_BYTE or DataBuffer.TYPE_USHORT
                img = new BufferedImage(icm, wr, false, null);
            } else if (bitspercomponent == 1 || bitspercomponent == 2 || bitspercomponent == 4) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_DeviceGray_124");
                //byte[] data = getDecodedStreamBytes();
                //int data_length = data.length;
                DataBuffer db = new DataBufferByte(data, dataLength);
                WritableRaster wr = Raster.createPackedRaster(db, width, height, bitspercomponent, new Point(0, 0));
                int[] cmap = null;
                if (bitspercomponent == 1) {
                    boolean defaultDecode = (0.0f == ((Number) decode.elementAt(0)).floatValue());
                    cmap = defaultDecode ? GRAY_1_BIT_INDEX_TO_RGB : GRAY_1_BIT_INDEX_TO_RGB_REVERSED;
                } else if (bitspercomponent == 2)
                    cmap = GRAY_2_BIT_INDEX_TO_RGB;
                else if (bitspercomponent == 4)
                    cmap = GRAY_4_BIT_INDEX_TO_RGB;
                ColorModel cm = new IndexColorModel(bitspercomponent, cmap.length, cmap, 0, false, -1, db.getDataType());
                img = new BufferedImage(cm, wr, false, null);
            } else if (bitspercomponent == 8) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_DeviceGray_8");
                //byte[] data = getDecodedStreamBytes();
                //int data_length = data.length;
                DataBuffer db = new DataBufferByte(data, dataLength);
                SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(),
                        width, height, 1, width, new int[]{0});
                WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
                // apply decode array manually
                float[] origValues = new float[3];
                int maxValue = ((int) Math.pow(2, bitspercomponent)) - 1;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        wr.getPixel(x, y, origValues);
                        origValues = getNormalizedComponents(
                                (byte[])wr.getDataElements(x,y,null), decode, maxValue);
                        float gray = origValues[0] *255;
                        byte rByte = (gray < 0) ? (byte) 0 : (gray > 255) ? (byte) 0xFF : (byte) gray;
                        origValues[0] = rByte;
                        wr.setPixel(x, y, origValues);
                    }
                }
                ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
                ColorModel cm = new ComponentColorModel(cs, new int[]{bitspercomponent},
                        false, false, ColorModel.OPAQUE, db.getDataType());
                img = new BufferedImage(cm, wr, false, null);
            }
            // apply explicit mask
            if (maskImage != null){
                applyExplicitMask(img, maskImage);
            }
            // apply soft mask
            if (smaskImage != null){
                img = alterBufferedImage(img, smaskImage, maskImage, maskMinRGB, maskMaxRGB);
            }
        } else if (colourSpace instanceof DeviceRGB) {
            if (bitspercomponent == 8) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_DeviceRGB_8");
                checkMemory(width * height * 4);
                boolean usingAlpha = smaskImage != null || maskImage != null || ((maskMinRGB != null) && (maskMaxRGB != null));
                if (Tagger.tagging)
                    Tagger.tagImage("RasterFromBytes_DeviceRGB_8_alpha=" + usingAlpha);
                int type = usingAlpha ? BufferedImage.TYPE_INT_ARGB :
                        BufferedImage.TYPE_INT_RGB;
                img = new BufferedImage(width, height, type);
                int[] dataToRGB = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
                copyDecodedStreamBytesIntoRGB(dataToRGB);
                if (usingAlpha)
                    alterBufferedImage(img, smaskImage, maskImage, maskMinRGB, maskMaxRGB);
            }
        } else if (colourSpace instanceof DeviceCMYK) {
            // TODO Look at doing CMYK properly, fallback code is very slow.
            if (false && bitspercomponent == 8) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_DeviceCMYK_8");
                DataBuffer db = new DataBufferByte(data, dataLength);
                int[] bandOffsets = new int[colorSpaceCompCount];
                for (int i = 0; i < colorSpaceCompCount; i++)
                    bandOffsets[i] = i;
                SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(), width, height, colorSpaceCompCount, colorSpaceCompCount * width, bandOffsets);
                WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
                //WritableRaster wr = Raster.createInterleavedRaster( db, width, height, colorSpaceCompCount*width, colorSpaceCompCount, bandOffsets, new Point(0,0) );
                ColorSpace cs = null;
//                try {
                //cs = new ColorSpaceCMYK(); //ColorSpace.getInstance( ColorSpace.CS_PYCC );//ColorSpace.TYPE_CMYK );
                ///cs = ColorSpaceWrapper.getICCColorSpaceInstance("C:\\Documents and Settings\\Mark Collette\\IdeaProjects\\TestJAI\\CMYK.pf");
//                }
//                catch (Exception csex) {
//                    if (logger.isLoggable(Level.FINE)) {
//                        logger.fine("Problem loading CMYK ColorSpace");
//                    }
//                }
                int[] bits = new int[colorSpaceCompCount];
                for (int i = 0; i < colorSpaceCompCount; i++)
                    bits[i] = bitspercomponent;
                ColorModel cm = new ComponentColorModel(cs, bits, false, false, ColorModel.OPAQUE, db.getDataType());
                img = new BufferedImage(cm, wr, false, null);
            }
        } else if (colourSpace instanceof Indexed) {
            if (bitspercomponent == 1 || bitspercomponent == 2 || bitspercomponent == 4) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_Indexed_124");
                colourSpace.init();
                Color[] colors = ((Indexed) colourSpace).accessColorTable();
                int[] cmap = new int[(colors == null) ? 0 : colors.length];
                for (int i = 0; i < cmap.length; i++) {
                    cmap[i] = colors[i].getRGB();
                }
                int cmapMaxLength = 1 << bitspercomponent;
                if (cmap.length > cmapMaxLength) {
                    int[] cmapTruncated = new int[cmapMaxLength];
                    System.arraycopy(cmap, 0, cmapTruncated, 0, cmapMaxLength);
                    cmap = cmapTruncated;
                }
                boolean usingIndexedAlpha = maskMinIndex >= 0 && maskMaxIndex >= 0;
                boolean usingAlpha = smaskImage != null || maskImage != null ||
                        ((maskMinRGB != null) && (maskMaxRGB != null));
                if (Tagger.tagging)
                    Tagger.tagImage("RasterFromBytes_Indexed_124_alpha=" +
                            (usingIndexedAlpha ? "indexed" : (usingAlpha ? "alpha" : "false")));
                if (usingAlpha) {
                    DataBuffer db = new DataBufferByte(data, dataLength);
                    WritableRaster wr = Raster.createPackedRaster(db, width, height, bitspercomponent, new Point(0, 0));
                    ColorModel cm = new IndexColorModel(bitspercomponent, cmap.length, cmap, 0, false, -1, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                    img = alterBufferedImage(img, smaskImage, maskImage, maskMinRGB, maskMaxRGB);
                }
                else{
                    DataBuffer db = new DataBufferByte(data, dataLength);
                    WritableRaster wr = Raster.createPackedRaster(db, width, height, bitspercomponent, new Point(0, 0));
                    ColorModel cm = new IndexColorModel(bitspercomponent, cmap.length, cmap, 0, false, -1, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                }
            } else if (bitspercomponent == 8) {
                if (Tagger.tagging)
                    Tagger.tagImage("HandledBy=RasterFromBytes_Indexed_8");
                //byte[] data = getDecodedStreamBytes();
                //int data_length = data.length;
                colourSpace.init();
                Color[] colors = ((Indexed) colourSpace).accessColorTable();
                int colorsLength = (colors == null) ? 0 : colors.length;
                int[] cmap = new int[256];
                for (int i = 0; i < colorsLength; i++) {
                    cmap[i] = colors[i].getRGB();
                }
                for (int i = colorsLength; i < cmap.length; i++)
                    cmap[i] = 0xFF000000;

                boolean usingIndexedAlpha = maskMinIndex >= 0 && maskMaxIndex >= 0;
                boolean usingAlpha = smaskImage != null || maskImage != null || ((maskMinRGB != null) && (maskMaxRGB != null));
                if (Tagger.tagging)
                    Tagger.tagImage("RasterFromBytes_Indexed_8_alpha=" + (usingIndexedAlpha ? "indexed" : (usingAlpha ? "alpha" : "false")));
                if (usingIndexedAlpha) {
                    for (int i = maskMinIndex; i <= maskMaxIndex; i++)
                        cmap[i] = 0x00000000;
                    DataBuffer db = new DataBufferByte(data, dataLength);
                    SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(), width, height, 1, width, new int[]{0});
                    WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
                    ColorModel cm = new IndexColorModel(bitspercomponent, cmap.length, cmap, 0, true, -1, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                }else if (usingAlpha) {
                    checkMemory(width * height * 4);
                    int[] rgbaData = new int[width * height];
                    for (int index = 0; index < dataLength; index++) {
                        int cmapIndex = (data[index] & 0xFF);
                        rgbaData[index] = cmap[cmapIndex];
                    }
                    DataBuffer db = new DataBufferInt(rgbaData, rgbaData.length);
                    int[] masks = new int[]{0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000};
                    //SampleModel sm = new SinglePixelPackedSampleModel(
                    //    db.getDataType(), width, height, masks );
                    WritableRaster wr = Raster.createPackedRaster(db, width, height, width, masks, new Point(0, 0));
                    alterRasterRGBA(wr, smaskImage, maskImage, maskMinRGB, maskMaxRGB);
                    ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                    ColorModel cm = new DirectColorModel(cs, 32, 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000, false, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                }else {
                    DataBuffer db = new DataBufferByte(data, dataLength);
                    SampleModel sm = new PixelInterleavedSampleModel(db.getDataType(), width, height, 1, width, new int[]{0});
                    WritableRaster wr = Raster.createWritableRaster(sm, db, new Point(0, 0));
                    ColorModel cm = new IndexColorModel(bitspercomponent, cmap.length, cmap, 0, false, -1, db.getDataType());
                    img = new BufferedImage(cm, wr, false, null);
                }
            }
        }
        return img;
    }

    /**
     * Parses the image stream and creates a Java Images object based on the
     * the given stream and the supporting paramaters.
     *
     * @param width         dimension of new image
     * @param height        dimension of new image
     * @param colorSpace    colour space of image
     * @param imageMask     true if the image has a imageMask, false otherwise
     * @param fill          colour pased in via graphic state, used to fill in background
     * @param bitsPerColour number of bits used in a colour
     * @param decode        Decode attribute values from PObject
     * @return valid java image from the PDF stream
     */
    private BufferedImage parseImage(
            int width,
            int height,
            PColorSpace colorSpace,
            boolean imageMask,
            Color fill,
            int bitsPerColour,
            Vector decode,
            byte[] baCCITTFaxData,
            BufferedImage smaskImage,
            BufferedImage maskImage,
            int[] maskMinRGB, int[] maskMaxRGB) {
        if (Tagger.tagging)
            Tagger.tagImage("HandledBy=ParseImage");

        // store for manipulating bits in image
        int[] imageBits = new int[width];

        // RGB value for colour used as fill for image
        int fillRGB = fill.getRGB();

        // Number of colour components in image, should be 3 for RGB or 4
        // for ARGB.
        int colorSpaceCompCount = colorSpace.getNumComponents();
        boolean isDeviceRGB = colorSpace instanceof DeviceRGB;
        boolean isDeviceGray = colorSpace instanceof DeviceGray;

        // Max value used to represent a colour,  usually 255, min is 0
        int maxColourValue = ((1 << bitsPerColour) - 1);

        int f[] = new int[colorSpaceCompCount];
        float ff[] = new float[colorSpaceCompCount];

        // image mask from
        int imageMaskValue = ((Number) decode.elementAt(0)).intValue();

        // decode decode.
        float[] decodeArray = null;
        if (decode != null) {
            decodeArray = new float[decode.size()];
            for (int i = 0; i < decodeArray.length; i++) {
                decodeArray[i] = ((Number) decode.elementAt(i)).floatValue();
            }
        }

//        System.out.println("image size " + width + "x" + height +
//                           "\n\tBitsPerComponent " + bitsPerColour +
//                           "\n\tColorSpace " + colorSpaceCompCount +
//                           "\n\tFill " + fillRGB +
//                           "\n\tDecode " + decode +
//                           "\n\tImageMask " + imageMask +
//                           "\n\tMaxColorValue " + maxColourValue +
//                           "\n\tColourSpace " + colorSpace.toString() +
//                           "\n\tLength " + streamInput.getLength());

        // Do a rough check for memory need for this image, we want to over
        // estimate the memory needed so that we minimize the change of
        // a out of memory error.  Because of image caching, there is no
        // significant performance hit, as loading the image from file is much
        // faster then parsing it from the decoded byte stream.
        int memoryNeeded = (width * height * 4); // ARGB
        checkMemory(memoryNeeded);

        // Create the memory hole where where the buffered image will be writen
        // too, bit by painfull bit.
        BufferedImage bim = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // create the buffer and get the first series of bytes from the cached
        // stream
        BitStream in;
        if (baCCITTFaxData != null) {
            in = new BitStream(new ByteArrayInputStream(baCCITTFaxData));
        } else {
            InputStream dataInput = getInputStreamForDecodedStreamBytes();
            if (dataInput == null)
                return null;
            in = new BitStream(dataInput);
        }

        try {
            // Start encoding bit stream into an image,  we work one pixel at
            // a time,  and grap the need bit information for the images
            // colour space and bits per colour
            for (int y = 0; y < height; y++) {

                for (int x = 0; x < width; x++) {

                    // if image has mask apply it
                    if (imageMask) {
                        int bit = in.getBits(bitsPerColour);
                        bit = (bit == imageMaskValue) ? fillRGB : 0x00000000;
                        imageBits[x] = bit;
                    }
                    // other wise start colour bit parsing
                    else {
                        // set some default values
                        int red = 255;
                        int blue = 255;
                        int green = 255;
                        int alpha = 255;

                        // indexed colour
                        if (colorSpaceCompCount == 1) {
                            // get value used for this bit
                            int bit = in.getBits(bitsPerColour);
                            // check decode array if a colour inversion is needed
                            if (decodeArray != null) {
                                // if index 0 > index 1 then we have a need for ainversion
                                if (decodeArray[0] > decodeArray[1]) {
                                    bit = (bit == maxColourValue) ? 0x00000000 : maxColourValue;
                                }
                            }

                            if (isDeviceGray) {
                                if (bitsPerColour == 1)
                                    bit = GRAY_1_BIT_INDEX_TO_RGB[bit];
                                else if (bitsPerColour == 2)
                                    bit = GRAY_2_BIT_INDEX_TO_RGB[bit];
                                else if (bitsPerColour == 4)
                                    bit = GRAY_4_BIT_INDEX_TO_RGB[bit];
                                else if (bitsPerColour == 8) {
                                    bit = ((bit << 24) |
                                            (bit << 16) |
                                            (bit << 8) |
                                            bit);
                                }
                                imageBits[x] = bit;
                            } else {
                                f[0] = bit;
                                colorSpace.normaliseComponentsToFloats(f, ff, maxColourValue);

                                Color color = colorSpace.getColor(ff);
                                imageBits[x] = color.getRGB();
                            }
                        }
                        // normal RGB colour
                        else if (colorSpaceCompCount == 3) {
                            // We can have an ICCBased color space that has 3 components,
                            //  but where we can't assume it's RGB.
                            // But, when it is DeviceRGB, we don't want the performance hit
                            //  of converting the pixels via the PColorSpace, so we'll
                            //  break this into the two cases
                            if (isDeviceRGB) {
                                red = in.getBits(bitsPerColour);
                                green = in.getBits(bitsPerColour);
                                blue = in.getBits(bitsPerColour);
                                // combine the colour together
                                imageBits[x] = (alpha << 24) | (red << 16) |
                                        (green << 8) | blue;
                            } else {
                                for (int i = 0; i < colorSpaceCompCount; i++) {
                                    f[i] = in.getBits(bitsPerColour);
                                }
                                PColorSpace.reverseInPlace(f);
                                colorSpace.normaliseComponentsToFloats(f, ff, maxColourValue);
                                Color color = colorSpace.getColor(ff);
                                imageBits[x] = color.getRGB();
                            }
                        }
                        // normal aRGB colour,  this could use some more
                        // work for optimizing.
                        else if (colorSpaceCompCount == 4) {
                            for (int i = 0; i < colorSpaceCompCount; i++) {
                                f[i] = in.getBits(bitsPerColour);
                            }
                            PColorSpace.reverseInPlace(f);
                            colorSpace.normaliseComponentsToFloats(f, ff, maxColourValue);
                            Color color = colorSpace.getColor(ff);
                            imageBits[x] = color.getRGB();
                        }
                        // else just set pixel with the default values
                        else {
                            // compine the colour together
                            imageBits[x] = (alpha << 24) | (red << 16) |
                                    (green << 8) | blue;
                        }
                    }
                }
                // Assign the new bits for this pixel
                bim.setRGB(0, y, width, 1, imageBits, 0, 1);
            }
            // final clean up.
            in.close();
            in = null;

            if (smaskImage != null || maskImage != null || maskMinRGB != null || maskMaxRGB != null) {
                WritableRaster wr = bim.getRaster();
                alterRasterRGBA(wr, smaskImage, maskImage, maskMinRGB, maskMaxRGB);
            }
        }
        catch (IOException e) {
            logger.log(Level.FINE, "Error parsing image.", e);
        }

        return bim;
    }

    private BufferedImage putIntoImageCache(BufferedImage bim, int width, int height, boolean allowScaling) {
        // create new Image cache if needed.
        if (image == null) {
            image = new ImageCache(library);
        }

        boolean setIsScaledOnImageCache = false;
        if (allowScaling && scaleImages && !image.isScaled()) {
            boolean canScale = checkMemory(
                    Math.max(width, bim.getWidth()) *
                            Math.max(height, bim.getHeight()) *
                            Math.max(4, bim.getColorModel().getPixelSize()));
            if (canScale) {
                // To limit the number of image file cache writes this scaling
                //  algorithem works with the buffered image in memory
                bim = ImageCache.scaleBufferedImage(bim, width, height);
                setIsScaledOnImageCache = true;
            }
        }

        // The checkMemory call above can make us lose our ImageCache
        synchronized (imageLock) {
            if (image == null) {
                image = new ImageCache(library);
            }
            // cache the new image
            if (setIsScaledOnImageCache)
                image.setIsScaled(true);
            image.setImage(bim);

            // read the image from the cache and return to caller
            bim = image.readImage();
        }
        return bim;
    }

    /**
     * Does the image have an ImageMask.
     */
    public boolean isImageMask() {
        Object o = library.getObject(entries, "ImageMask");
        return (o != null)
                ? (o.toString().equals("true") ? true : false)
                : false;
    }

    public boolean getBlackIs1(Library library, Hashtable decodeParmsDictionary) {
        Boolean blackIs1 = getBlackIs1OrNull(library, decodeParmsDictionary);
        if (blackIs1 != null)
            return blackIs1.booleanValue();
        return false;
    }

    /**
     * If BlackIs1 was not specified, then return null, instead of the
     * default value of false, so we can tell if it was given or not
     */
    public Boolean getBlackIs1OrNull(Library library, Hashtable decodeParmsDictionary) {
        Object blackIs1Obj = library.getObject(decodeParmsDictionary, "BlackIs1");
        if (blackIs1Obj != null) {
            if (blackIs1Obj instanceof Boolean) {
                return (Boolean) blackIs1Obj;
            } else if (blackIs1Obj instanceof String) {
                String blackIs1String = (String) blackIs1Obj;
                if (blackIs1String.equalsIgnoreCase("true"))
                    return Boolean.TRUE;
                else if (blackIs1String.equalsIgnoreCase("t"))
                    return Boolean.TRUE;
                else if (blackIs1String.equals("1"))
                    return Boolean.TRUE;
                else if (blackIs1String.equalsIgnoreCase("false"))
                    return Boolean.FALSE;
                else if (blackIs1String.equalsIgnoreCase("f"))
                    return Boolean.FALSE;
                else if (blackIs1String.equals("0"))
                    return Boolean.FALSE;
            }
        }
        return null;
    }

    /**
     * Allow access to seakable intput so that pattern object can
     * be corrrectly created.
     *
     * @return stream istnaces SeekableInputConstrainedWrapper
     */
    public SeekableInputConstrainedWrapper getStreamInput() {
        return streamInput;
    }

    /**
     * Return a string description of the object.  Primarly used for debugging.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append("STREAM= ");
        sb.append(entries);
        if (getPObjectReference() != null) {
            sb.append("  ");
            sb.append(getPObjectReference());
        }
        return sb.toString();
    }

    /**
     * Clips the value according to the specified floor and ceiling.
     * @param floor floor value of clip
     * @param ceiling ceiling value of clip
     * @param value value to clip.
     * @return clipped value.
     */
    private static double clip(double floor, double ceiling, double value) {
        if (value < floor){
            value = floor;
        }
        if (value > ceiling){
            value = ceiling;
        }
        return value;
    }

    private static void displayImage(BufferedImage bufferedImage){

        int width2 = bufferedImage.getWidth();
        int height2 = bufferedImage.getHeight();
        final BufferedImage bi = bufferedImage;
//        final BufferedImage bi = new BufferedImage(width2,height2, BufferedImage.TYPE_INT_RGB);
//        for (int y = 0; y < height2; y++) {
//            for (int x = 0; x < width2; x++) {
//                if (bufferedImage.getRGB(x,y) != -1){
//                    bi.setRGB(x, y, Color.red.getRGB());
//                }
//                else{
//                    bi.setRGB(x, y, Color.green.getRGB());
//                }
//            }
//        }
        final JFrame f = new JFrame("Test");
        f.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        JComponent image = new JComponent() {
            @Override
            public void paint(Graphics g_) {
                super.paint(g_);
                g_.drawImage(bi, 0, 0, f);
            }
        };
        image.setPreferredSize(new Dimension(bi.getWidth(), bi.getHeight()));
        image.setSize(new Dimension(bi.getWidth(), bi.getHeight()));

        JPanel test = new JPanel();
        test.setPreferredSize(new Dimension(1200,1200));
        JScrollPane tmp = new JScrollPane(image);
        tmp.revalidate();
        f.setSize(new Dimension(800, 800));
        f.getContentPane().add(tmp);
        f.validate();
        f.setVisible(true);

    }

}
