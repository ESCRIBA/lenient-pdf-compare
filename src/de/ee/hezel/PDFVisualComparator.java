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
import de.ee.hezel.model.PDFInfoHolder.DifferenceType;
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
    private PDFInfoHolder pdfInfoHolder;
    private PDFVisualiseDifference pdfVisualiseDifference;
	private File targetFolder;
	
	public PDFVisualComparator(File outputDir, ICompareLogger diffLog, PDFInfoHolder pdfih)
	{
		pdfVisualiseDifference = new PDFVisualiseDifference(outputDir, diffLog, pdfih);
		
		setDifferenceLogger(diffLog);
		pdfInfoHolder = pdfih;
		targetFolder = outputDir;
	}
	
    /**
     * start visual comparison
     * 
     * @param targetFolder
     */
    public void compare() {
    	
    	// could not find the new generated pdf document
    	if(pdfInfoHolder.getDifferent() == DifferenceType.MISSINGDOCUMENT)
    	{
    		missingDocument(pdfInfoHolder.getPDF1());
    		return;
    	}
    	
        PDFFile pdf1 = pdfInfoHolder.getPDF1();
        PDFFile pdf2 = pdfInfoHolder.getPDF2();

        // find all differences on all pages
        for (int i = 1; i <= pdf1.getNumPages(); i++) {
        	// get the current page
            PDFPage pagePDF1 = pdf1.getPage(i);
            PDFPage pagePDF2 = pdf2.getPage(i);

            // missing a page
            if(pagePDF2 == null)
            {
            	missingPage(pagePDF1, i);
            	continue;
            }
            
            // find real visual differences
            findVisualDifferences(i, pagePDF1, pagePDF2, targetFolder);
        }

        // if there is a difference on one of the 
        // pages mark the entire pdf as different
        pdfInfoHolder.checkDifference();
    }
    
    private void missingPage(PDFPage pagePDF, int pageNum)
    {
    	try {
        	BufferedImage diffimg = pdfVisualiseDifference.drawPageInRed(pagePDF);
        	
    		// save the result
    		String pathName = targetFolder+"/"+pdfInfoHolder.getFilename() + "_pdf/";
    		File dir = new File(pathName);  dir.mkdirs();		
    		ImageIO.write(diffimg, "PNG", new File(pathName+"page_"+pageNum+".png"));
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
    }
    
    private void missingDocument(PDFFile pdf)
    {
        for (int i = 1; i <= pdf.getNumPages(); i++) {
        	// get the current page
            PDFPage pagePDF = pdf.getPage(i);
            missingPage(pagePDF, i);
        }
    }
    
    private void findVisualDifferences(int pageNum, PDFPage pagePDF1, PDFPage pagePDF2, File targetFolder)
    {
    	 try {
    		 	// convert the page in a image
    		 	BufferedImage pageImgPDF1 = PDFVisualiseDifference.convertPage(pagePDF1);
    		 	BufferedImage pageImgPDF2 = PDFVisualiseDifference.convertPage(pagePDF2);
           
	            // get the structure elements for this page
	            PDFPageHolder pdfPageHolder1 = pdfInfoHolder.getPDFStructure1().getPageHolder(pageNum - 1);
	            PDFPageHolder pdfPageHolder2 = pdfInfoHolder.getPDFStructure2().getPageHolder(pageNum - 1);
	
	            Set<PDFEntryHolder> entryHolders = new HashSet<PDFEntryHolder>(pdfPageHolder1.getElements());
	            entryHolders.addAll(pdfPageHolder2.getElements());
	
	            // compare both images only at those place where a entryholder says
	            comparePDFEntries(pageImgPDF1, pageImgPDF2, entryHolders, pageNum);
	
	            // check if a difference was found
	            pdfPageHolder1.checkDifference();
	            pdfPageHolder2.checkDifference();
	
	            // mark the found differences visual
	            if (targetFolder != null && (pdfPageHolder1.isDifferent() || pdfPageHolder2.isDifferent())) 
	            {
	            	// create a illustration which shows the differences
	                BufferedImage diffimg = pdfVisualiseDifference.visualiseDifferences(pageImgPDF1, pageImgPDF2, entryHolders);
	
	                // save the difference image if desired
	                String pathName = targetFolder + "/" + pdfInfoHolder.getFilename() + "_pdf/";
	                File dir = new File(pathName);
	                dir.mkdirs();
	                ImageIO.write(diffimg, "PNG", new File(pathName + "page_" + pageNum + ".png"));
	            }
         
			} catch (Exception e) {
				log.error(pdfInfoHolder.getFilename()+": "+e.getMessage(), e);
			}
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
		int[] img1Pixels = pdfVisualiseDifference.getPixelArray(1, pageWidth*pageHeight);
		int[] img2Pixels = pdfVisualiseDifference.getPixelArray(2, pageWidth*pageHeight);
		
		// copy pixel array
		pageImgPDF1.getRGB(0, 0, pageWidth, pageHeight, img1Pixels, 0, pageWidth);
		pageImgPDF2.getRGB(0, 0, pageWidth, pageHeight, img2Pixels, 0, pageWidth);
		
		// search for differences inside the area of all elements
		for (PDFEntryHolder pdfEntryHolder : entryHolders) 
		{
			//  pixel different for different metricies
			int diffValue = 0, diffValueL1 = 0, diffValueL2 = 0;
			
			// dimension of the entry holder for the current zoom factor
			int entryX = (int)(pdfEntryHolder.getX() * PDFVisualiseDifference.IMAGE_SCALER);
			int entryY = (int)(pdfEntryHolder.getY() * PDFVisualiseDifference.IMAGE_SCALER);
			int entryWidth = (int)(pdfEntryHolder.getWidth() * PDFVisualiseDifference.IMAGE_SCALER);
			int entryHeight = (int)(pdfEntryHolder.getHeight() * PDFVisualiseDifference.IMAGE_SCALER);
			
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
					
					// pixel at the edge have a higher value
					if(pdfEntryHolder instanceof PDFImageHolder)
					{
						double yDeviationToBorder = (double)((entryY+(entryHeight/2)) - y) / (entryHeight/2);
						double xDeviationToBorder = (double)((entryX+(entryWidth/2)) - x) / (entryWidth/2);
						double maxDeviation = ((yDeviationToBorder < xDeviationToBorder) ? xDeviationToBorder : yDeviationToBorder);
						double sqrtDeviation = ((maxDeviation*10)*(maxDeviation*10))/10;
						pixelImportance = 1 + sqrtDeviation;
					}
					
					// count the different pixel
					if(meanColorDiff > 5)
						diffValue += 1 * pixelImportance;
					
					// calc L1 and L2 metric
					diffValueL1 += meanColorDiff * pixelImportance;
					diffValueL2 += (meanColorDiff * pixelImportance) * (meanColorDiff * pixelImportance);
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
		
		int entryWidth = (int)(entryHolder.getWidth() * PDFVisualiseDifference.IMAGE_SCALER);
		int entryHeight = (int)(entryHolder.getHeight() * PDFVisualiseDifference.IMAGE_SCALER);
		int pixelAmount = entryHeight * entryWidth;
		double relativeDiff = (double)diffValue / pixelAmount;
		
		// does the value exceed a threshold
		if(relativeDiff > 0.1) // 10%
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
}
