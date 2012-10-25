/*
* PDFVisualiseDifference
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
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
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
 * illustrates the difference of the 2 pdf documents
 * 
 * @author hezeln
 *
 */
public class PDFVisualiseDifference extends AbstractPDFCompare {

	static Colorize colorizer = new Colorize();
	static Logger log = Logger.getLogger(PDFVisualiseDifference.class.getName());
	
	// a list of int arrays which gets reused
    ThreadLocal<Map<Integer, intArray>> intarrays = new ThreadLocal<Map<Integer,intArray>>();
	final static double IMAGE_SCALER = 2.1389;
	private PDFInfoHolder pdfInfoHolder;
	private File targetFolder;
	
	public PDFVisualiseDifference(File outputDir, ICompareLogger diffLog, PDFInfoHolder pdfih)
	{
		setDifferenceLogger(diffLog);
		pdfInfoHolder = pdfih;
		targetFolder = outputDir;
	}
	
	
	/**
	 * If the targetFolder is not null, every pdf document
	 * which does contain differences. Gets its own difference-image.
	 * Within those images, the difference are hightlighted
	 * 
	 * @param pdfHolders
	 * @param targetFolder
	 */
	public void visualise() 
	{
		// should the differences be saved in images
		if(targetFolder == null)
			return;

		// did found any difference
		if(pdfInfoHolder.isDifferent())
		{			
			try {
		        PDFFile pdf1 = pdfInfoHolder.getPDF1();
		        PDFFile pdf2 = pdfInfoHolder.getPDF2();
		        
				// check each page
				int numPgs = pdf1.getNumPages();
				for (int i = 1; i <= numPgs; i++) 
				{
					// get the page structure
					PDFPageHolder pdfPageHolder1 = pdfInfoHolder.getPDFStructure1().getPageHolder(i-1);
					PDFPageHolder pdfPageHolder2 = pdfInfoHolder.getPDFStructure2().getPageHolder(i-1);
					
					// does the pdf have the same amount of pages
					if(pdfPageHolder1 == null || pdfPageHolder2 == null)
						continue;
					
					// are there any differences on this page
					if(!pdfPageHolder1.isDifferent() && !pdfPageHolder2.isDifferent())
						continue;
					
					// get all element on this page
					Set<PDFEntryHolder> entryHolders = new HashSet<PDFEntryHolder>(pdfPageHolder1.getElements());
					entryHolders.addAll(pdfPageHolder2.getElements());
			
		            PDFPage pagePDF1 = pdf1.getPage(i);
		            PDFPage pagePDF2 = pdf2.getPage(i);

		            if(pagePDF1 == null || pagePDF2 == null)
		            {
		            	continue;
		            }
		            
		            // convert the page in a image
	                BufferedImage pageImgPDF1 = convertPage(pagePDF1);
	                BufferedImage pageImgPDF2 = convertPage(pagePDF2);
		            
	                // create a illustration which shows the differences
	                BufferedImage diffimg = visualiseDifferences(pageImgPDF1, pageImgPDF2, entryHolders);
						
					// save the result
					String pathName = targetFolder+"/"+pdfInfoHolder.getFilename() + "_pdf/";
					File dir = new File(pathName);  dir.mkdirs();		
					ImageIO.write(diffimg, "PNG", new File(pathName+"page_"+i+".png"));
				}
				
			} catch (Exception e) {
				log.error(pdfInfoHolder.getFilename()+": "+e.getMessage(),e);
			}
		}
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
	public BufferedImage visualiseDifferences(BufferedImage pageImgPDF1, BufferedImage pageImgPDF2, Set<PDFEntryHolder> entryHolders) 
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
	
	public BufferedImage drawPageInRed(PDFPage pagePDF)
	{
		// convert the page in a image
        BufferedImage diffimg = convertPage(pagePDF);
        
        // colorize the image
        colorizer.doColorize(diffimg);

        return diffimg;
	}
	
	/**
	 * deep copy of an image
	 * 
	 * @param bi
	 * @return
	 */
	private  BufferedImage deepCopy(BufferedImage bi) 
	{
		 ColorModel cm = bi.getColorModel();
		 boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
		 WritableRaster raster = bi.copyData(null);
		 return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
	}
	
	/**
	 * convert the page into an image
	 * 
	 * @param page
	 * @return
	 * @throws IllegalArgumentException
	 */
	public static BufferedImage convertPage(PDFPage page) throws IllegalArgumentException {

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
	public int[] getPixelArray(int arraynumber, int arraysize)
	{
	    Map<Integer, intArray> map = intarrays.get();
	    if (null == map) {
	        map = new HashMap<Integer, PDFVisualiseDifference.intArray>(3);
            intarrays.set(map);
	    }
		if(!map.containsKey(arraynumber))
			map.put(arraynumber, new intArray(arraysize));
			
		intArray intarr = map.get(arraynumber);
			
		if(intarr.arr.length != arraysize)
			intarr.arr = new int[arraysize];
		
		return intarr.arr;
	}
	
	public class intArray
	{
		public int[] arr;
		public intArray(int size)
		{
			arr = new int[size];
		}
	}
}
