/*
* PDFVisualComparator
* 
* Copyright (c) 2012, E&E information consultants AG. All rights reserved.
* Authors:
*   Peter Jentsch
*   Nico Hezel
*   
* This library is free software; you can redistribute it and/or
* modify it under the terms of the GNU Lesser General Public
* License as published by the Free Software Foundation; either
* version 2.1 of the License, or (at your option) any later version.
*
* This library is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this library; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
* MA 02110-1301 USA
*/
package de.ee.hezel;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.log4j.Logger;

import com.sun.pdfview.PDFFile;
import com.sun.pdfview.PDFPage;

import de.ee.hezel.logger.ICompareLogger;
import de.ee.hezel.model.PDFInfoHolder;
import de.ee.hezel.model.PDFPageHolder;
import de.ee.hezel.model.pdfelemente.PDFEntryHolder;
import de.ee.hezel.model.pdfelemente.PDFImageHolder;
import de.ee.hezel.model.pdfelemente.PDFTextHolder;

/**
 * 
 * @author hezeln
 *
 */
public class PDFVisualComparator extends AbstractPDFCompare {

	static Logger log = Logger.getLogger(PDFVisualComparator.class.getName());
	
	// a list of int arrays which gets reused
    ThreadLocal<Map<Integer, intArray>> intarrays = new ThreadLocal<Map<Integer,intArray>>();
	final static double IMAGE_SCALER = 2.1389;
	private PDFInfoHolder pdfInfoHolder;

	public PDFVisualComparator(ICompareLogger diffLog, PDFInfoHolder pdfih)
	{
		setDifferenceLogger(diffLog);
		pdfInfoHolder = pdfih;
	}
	
    /**
     * start visual comparision
     * 
     * @param targetFolder
     */
    public void compare(File targetFolder) {
        PDFFile pdf1 = pdfInfoHolder.getPDF1();
        PDFFile pdf2 = pdfInfoHolder.getPDF2();

        int numPgs = pdf1.getNumPages();
        for (int i = 1; i <= numPgs; i++) {
        	// get the current page
            PDFPage pagePDF1 = pdf1.getPage(i);
            PDFPage pagePDF2 = pdf2.getPage(i);

            if(pagePDF1 == null || pagePDF2 == null)
            {
            	continue;
            }
            
            try {
                // convert the page in a image
                BufferedImage pageImgPDF1 = convertPage(pagePDF1);
                BufferedImage pageImgPDF2 = convertPage(pagePDF2);
              
	            // get the structure elements for this page
	            PDFPageHolder pdfPageHolder1 = pdfInfoHolder.getPDFStructure1().getPageHolder(i - 1);
	            PDFPageHolder pdfPageHolder2 = pdfInfoHolder.getPDFStructure2().getPageHolder(i - 1);
	
	            Set<PDFEntryHolder> entryHolders = new HashSet<PDFEntryHolder>(pdfPageHolder1.getElements());
	            entryHolders.addAll(pdfPageHolder2.getElements());
	
	            // compare both images only at those place where a entryholder says
	            comparePDFEntries(pageImgPDF1, pageImgPDF2, entryHolders, i);
	
	            // check if a difference was found
	            pdfPageHolder1.checkDifference();
	            pdfPageHolder2.checkDifference();
	
	            // mark the found differences visual
	            if (targetFolder != null && (pdfPageHolder1.isDifferent() || pdfPageHolder2.isDifferent())) {
	            	
	            	// create a illustration which shows the differences
	                BufferedImage diffimg = visualiseDifferences(pageImgPDF1, pageImgPDF2, entryHolders);
	
	                // save the difference image if desired
	                String pathName = targetFolder + "/" + pdfInfoHolder.getFilename() + "_pdf/";
	                File dir = new File(pathName);
	                dir.mkdirs();
	                try {
	                    ImageIO.write(diffimg, "PNG", new File(pathName + "page_" + i + ".png"));
	                } catch (IOException e) {
	                    throw new RuntimeException("Unable to write difference to file: " + e.getMessage(), e);
	                }
	            }
            
			} catch (Exception e) {
				log.error(pdfInfoHolder.getFilename()+": "+e.getMessage(), e);
			}
        }

        // check if in the pdf document one of the pages does have a 
        // difference. if to mark the entire pdf as different
        pdfInfoHolder.checkDifference();
    }

	/**
	 * Create a difference image, based on the elements in the pdf.
	 * Elements which exists on the first pdf but not on the 2nd
	 * are colored in green. The other ways around gets colored in
	 * red. If both element cover each other they stay as they are.
	 * 
	 * If the content of an image is different the image gets 
	 * a read rectangle. Different text elements get underlined red.
	 * 
	 * @param pageImgPDF1
	 * @param entryHolders
	 * @throws IOException 
	 */
	private BufferedImage visualiseDifferences(BufferedImage pageImgPDF1, BufferedImage pageImgPDF2, Set<PDFEntryHolder> entryHolders) 
	{
		int pageWidth = pageImgPDF1.getWidth();
		int pageHeight = pageImgPDF1.getHeight();
		
		// 1-dimensional pixel array
		int[] img1Pixels = getPixelArray(1, pageWidth*pageHeight); 
		int[] img2Pixels = getPixelArray(2, pageWidth*pageHeight); 
		int[] diffPixels = getPixelArray(3, pageWidth*pageHeight);
		
		// copy all pixels from the image to the pixel array
		pageImgPDF1.getRGB(0, 0, pageWidth, pageHeight, img1Pixels, 0, pageWidth);
		pageImgPDF2.getRGB(0, 0, pageWidth, pageHeight, img2Pixels, 0, pageWidth);
		
		// copy the image to the output pixel array
		System.arraycopy(img1Pixels, 0, diffPixels, 0, pageWidth*pageHeight);
		
		// output image
		BufferedImage diffimg = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_RGB);
		
		for (PDFEntryHolder pdfEntryHolder : entryHolders) 
		{
			// check if there are any differences
			if(!pdfEntryHolder.isDifferent())
				continue;
			
			// dimension of the entry holder with the current zoom factor
			int entryX = (int)(pdfEntryHolder.getX() * IMAGE_SCALER);
			int entryY = (int)(pdfEntryHolder.getY() * IMAGE_SCALER);
			int entryWidth = (int)(pdfEntryHolder.getWidth() * IMAGE_SCALER);
			int entryHeight = (int)(pdfEntryHolder.getHeight() * IMAGE_SCALER);
			
			// draw pixel red or green, if the elements does not cover each other
			for (int y = ((entryY < 0) ? 0 : entryY); y < entryY+entryHeight; y++) {
                if (y*pageWidth >= img1Pixels.length) {
                    log.error(pdfInfoHolder.getFilename()+": graphics boundaries exceed page boundaries. y=" + y + ", pageWidth=" + pageWidth);
                    break;
                }

				for (int x = ((entryX < 0) ? 0 : entryX); x < entryX+entryWidth; x++) {
					
					// position in the 1d pixel array
					int pos = y * pageWidth + x;

                    if (pos >= img1Pixels.length) {
                        log.error(pdfInfoHolder.getFilename()+": graphics boundaries exceed page boundaries. y=" + y + ", x=" + x + ", pageWidth=" + pageWidth);
                        break;
                    }
                    
                    // calc gray value for 1st image
					int r_img1 = (img1Pixels[pos] >> 16) & 255;
					int g_img1 = (img1Pixels[pos] >> 8) & 255;
					int b_img1 = (img1Pixels[pos]) & 255;
					int grey_img1 = (r_img1 + b_img1 + g_img1) / 3;

					// calc gray value for 2nd image
					int r_img2 = (img2Pixels[pos] >> 16) & 255;
					int g_img2 = (img2Pixels[pos] >> 8) & 255;
					int b_img2 = (img2Pixels[pos]) & 255;
					int grey_img2 = (r_img2 + b_img2 + g_img2) / 3;
				
					int diff = (grey_img1<grey_img2) ? grey_img2-grey_img1 : grey_img1-grey_img2;
					
					// decide for red, green or nothing
					if(diff > 5 && grey_img1 < grey_img2)
						diffPixels[pos] = (0xFF << 24) | (0xFF << 16) | (grey_img1 << 8) | grey_img1;
					else if(diff > 5 && grey_img1 > grey_img2)
						diffPixels[pos] = (0xFF << 24) | (grey_img2 << 16) | (0xFF << 8) | grey_img2;
				}
			}
		}
		
		// save the result in an image
		diffimg.setRGB(0, 0, pageWidth, pageHeight, diffPixels, 0, pageWidth);
		
		// draw a red rectangle around images or underline 
		// differences in text elements red
		Graphics g = diffimg.getGraphics();
		g.setColor(Color.RED);
		for (PDFEntryHolder pdfEntryHolder : entryHolders) 
		{
			// check if there are any differences
			if(!pdfEntryHolder.isDifferent())
				continue;
			
			int entryX = (int)(pdfEntryHolder.getX() * IMAGE_SCALER);
			int entryY = (int)(pdfEntryHolder.getY() * IMAGE_SCALER);
			int entryWidth = (int)(pdfEntryHolder.getWidth() * IMAGE_SCALER);
			int entryHeight = (int)(pdfEntryHolder.getHeight() * IMAGE_SCALER);
			
			// draw rectangle
			if(pdfEntryHolder instanceof PDFImageHolder)
				g.drawRect(entryX, entryY, entryWidth, entryHeight);
			// underline text
			else if(pdfEntryHolder instanceof PDFTextHolder)
				g.drawLine(entryX, entryY+entryHeight, entryX+entryWidth, entryY+entryHeight);
				
		}		
		
		
		return diffimg;
	}
	
	/**
	 * The given entry holder mark those places in the image, which are interesting.
	 * Compare the area around those places and mark them if their are different.
	 * 
	 * EntryHolder.isDifferent tells us if this place is visually different
	 * 
	 * @param pageImgPDF1
	 * @param pageImgPDF2
	 * @param entryHolders
	 */
	private void comparePDFEntries(BufferedImage pageImgPDF1, BufferedImage pageImgPDF2, Set<PDFEntryHolder> entryHolders, int pageNum)
	{
		int pageWidth = pageImgPDF1.getWidth();
		int pageHeight = pageImgPDF1.getHeight();
		
		// 1d pixel array
		int[] img1Pixels = getPixelArray(1, pageWidth*pageHeight);
		int[] img2Pixels = getPixelArray(2, pageWidth*pageHeight);
		
		// copy pixel array
		pageImgPDF1.getRGB(0, 0, pageWidth, pageHeight, img1Pixels, 0, pageWidth);
		pageImgPDF2.getRGB(0, 0, pageWidth, pageHeight, img2Pixels, 0, pageWidth);
		
		// search for differences inside the area of all elements
		for (PDFEntryHolder pdfEntryHolder : entryHolders) 
		{
			//  pixel different for different metricies
			int diffValue = 0, diffValueL1 = 0, diffValueL2 = 0;
			
			// dimension of the entry holder for the current zoom factor
			int entryX = (int)(pdfEntryHolder.getX() * IMAGE_SCALER);
			int entryY = (int)(pdfEntryHolder.getY() * IMAGE_SCALER);
			int entryWidth = (int)(pdfEntryHolder.getWidth() * IMAGE_SCALER);
			int entryHeight = (int)(pdfEntryHolder.getHeight() * IMAGE_SCALER);
			
			// pixel at the edge are sometimes more important
			double pixelImportance = 1;
			
			// search for different pixels 
			for (int y = ((entryY < 0) ? 0 : entryY); y < entryY+entryHeight; y++) {
                if (y * pageWidth >= img1Pixels.length) {
                    log.error(pdfInfoHolder.getFilename()+": graphics boundaries exceed page boundaries. y=" + y + ", pageWidth=" + pageWidth);
                    diffValue = Integer.MAX_VALUE;
                    break;
                }
				for (int x = ((entryX < 0) ? 0 : entryX); x < entryX+entryWidth; x++) {
					
					// pixel position in the 1d pixel array
					int pos = y * pageWidth + x;
                    if (pos >= img1Pixels.length) {
                        log.error(pdfInfoHolder.getFilename()+": graphics boundaries exceed page boundaries. y=" + y + ", x=" + x + ", pageWidth=" + pageWidth);
                        diffValue = Integer.MAX_VALUE;
                        break;
                    }
					
                    // calc gray value for 1st image
					int r_img1 = (img1Pixels[pos] >> 16) & 255;
					int g_img1 = (img1Pixels[pos] >> 8) & 255;
					int b_img1 = (img1Pixels[pos]) & 255;

					// calc gray value for 2nd image
					int r_img2 = (img2Pixels[pos] >> 16) & 255;
					int g_img2 = (img2Pixels[pos] >> 8) & 255;
					int b_img2 = (img2Pixels[pos]) & 255;

					int r_diff = (r_img1 < r_img2) ? r_img2-r_img1 : r_img1-r_img2;
					int g_diff = (g_img1 < g_img2) ? g_img2-g_img1 : g_img1-g_img2;
					int b_diff = (b_img1 < b_img2) ? b_img2-b_img1 : b_img1-b_img2;
					
					// calc average difference and maximal difference
					double meanColorDiff = (double)(r_diff+g_diff+b_diff) / 3;
					int maxColorDiff = (r_diff < g_diff) ? g_diff : r_diff;
					maxColorDiff = (maxColorDiff < b_diff) ? maxColorDiff : b_diff;
					
					// pixel at the edge have a higher value
					if(pdfEntryHolder instanceof PDFImageHolder)
					{
						double yDeviationToBorder = (double)((entryY+(entryHeight/2)) - y) / (entryHeight/2);
						double xDeviationToBorder = (double)((entryX+(entryWidth/2)) - x) / (entryWidth/2);
						double maxDeviation = ((yDeviationToBorder < xDeviationToBorder) ? xDeviationToBorder : yDeviationToBorder);
						double sqrtDeviation = ((maxDeviation*10)*(maxDeviation*10))/10;
						pixelImportance = 1 + sqrtDeviation;
					}
					
					// remember differences
					if(meanColorDiff > 5)
						diffValue += 1 * pixelImportance;
					
					diffValueL1 += meanColorDiff * pixelImportance;
					diffValueL2 += (meanColorDiff * meanColorDiff * pixelImportance);
				}
			}
			
			// necessary calculation for L2 metric
			diffValueL2 = (int)Math.sqrt(diffValueL2);
			
			// mark the entry holder as different, if the images
			// at this position differ from each other
			analyseDifference(diffValue, pdfEntryHolder, pageNum);
		}
	}
	
	/**
	 * calc if the difference for the given pdf element is strong enough
	 * to be count as difference.
	 * 
	 * @param diffValue
	 * @param entryHolder
	 */
	private void analyseDifference(int diffValue, PDFEntryHolder entryHolder, int pageNum)
	{
		boolean isDifferent = false;
		
		int entryWidth = (int)(entryHolder.getWidth() * IMAGE_SCALER);
		int entryHeight = (int)(entryHolder.getHeight() * IMAGE_SCALER);
		int pixelAmount = entryHeight * entryWidth;
		double relativeDiff = (double)diffValue / pixelAmount;
		
		// does the value exceed a threshold
		if(relativeDiff > 0.1)
		{
			isDifferent = true;
			
			DecimalFormat df = new DecimalFormat( "####.###" );
			if(entryHolder instanceof PDFTextHolder)
			{
				PDFTextHolder th = (PDFTextHolder)entryHolder;
				diff.log(pdfInfoHolder.getFilename()+": Text \""+th.getText()+"\" on page " 
						+ pageNum + " at position " + df.format(th.getX())+" | " + df.format(th.getY())
						+ " with size " + df.format(th.getWidth()) + " width and " + df.format(th.getHeight()) + " height looks different");
			}
			else
			{
				diff.log(pdfInfoHolder.getFilename()+": Image on page " + pageNum 
						+ " at position " + df.format(entryHolder.getX())+" | " + df.format(entryHolder.getY()) + " with size " 
						+ df.format(entryHolder.getWidth()) + " width and " + df.format(entryHolder.getHeight()) + " height looks different");
			}
		}
		
		entryHolder.setDifferent(isDifferent);
	}

	/**
	 * convert pdf page to image
	 * 
	 * @param page
	 * @return
	 * @throws IllegalArgumentException
	 */
	private BufferedImage convertPage(PDFPage page) throws IllegalArgumentException {

			// get the width and height for the doc at the default zoom
			Rectangle rect = new Rectangle(0, 0, (int) page.getBBox().getWidth(), (int) page.getBBox().getHeight());
			
			int pageWidth = (int) (rect.width * IMAGE_SCALER);
			int pageHeight = (int) (rect.height * IMAGE_SCALER);
			
			BufferedImage bImg = (BufferedImage) page.getImage(pageWidth, pageHeight, // width & height
					rect, // clip rect
					null, // null for the ImageObserver
					true, // fill background with white
					true // block until drawing is done
					);
			
			return bImg;
	}
	
	/**
	 * reuse the allocated memory 
	 * 
	 * @param arraynumber
	 * @param arraysize
	 * @return
	 */
	private int[] getPixelArray(int arraynumber, int arraysize)
	{
	    Map<Integer, intArray> map = intarrays.get();
	    if (null == map) {
	        map = new HashMap<Integer, PDFVisualComparator.intArray>(3);
            intarrays.set(map);
	    }
		if(!map.containsKey(arraynumber))
			map.put(arraynumber, new intArray(arraysize));
			
		intArray intarr = map.get(arraynumber);
			
		if(intarr.arr.length != arraysize)
			intarr.arr = new int[arraysize];
		
		return intarr.arr;
	}
	
	class intArray
	{
		public int[] arr;
		public intArray(int size)
		{
			arr = new int[size];
		}
	}

}
