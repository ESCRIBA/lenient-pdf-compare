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
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashSet;
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

	static Logger log = Logger.getLogger(PDFVisualiseDifference.class.getName());
	private PDFInfoHolder pdfInfoHolder;
	
	public PDFVisualiseDifference(ICompareLogger diffLog, PDFInfoHolder pdfih)
	{
		setDifferenceLogger(diffLog);
		pdfInfoHolder = pdfih;
	}
	
	
	/**
	 * If the targetFolder is not null, every pdf document
	 * which does contain differences. Gets its own difference-image.
	 * Within those images, the difference are hightlighted
	 * 
	 * @param pdfHolders
	 * @param targetFolder
	 */
	public void visualise(File targetFolder) 
	{
		// should the differences be saved in images
		if(targetFolder == null)
			return;

		// did found any difference
		if(pdfInfoHolder.isDifferent())
		{			
			try {
		        PDFFile pdf1 = pdfInfoHolder.getPDF1();
				
				// check each page
				int numPgs = pdf1.getNumPages();
				for (int i = 1; i <= numPgs; i++) 
				{
					// get the page structure
					PDFPageHolder pdfPageHolder1 = pdfInfoHolder.getPDFStructure1().getPageHolder(i-1);
					PDFPageHolder pdfPageHolder2 = pdfInfoHolder.getPDFStructure2().getPageHolder(i-1);
					
					// the pdfs does not have the same amount of pages
                    if(pdfPageHolder1 == null || pdfPageHolder2 == null)
                        continue;

					// are there any differences on this page
					if(!pdfPageHolder1.isDifferent() && !pdfPageHolder2.isDifferent())
						continue;
					
					// get all element on this page
					Set<PDFEntryHolder> entryHolders = new HashSet<PDFEntryHolder>(pdfPageHolder1.getElements());
					entryHolders.addAll(pdfPageHolder2.getElements());
					
					// convert the page into an image
					PDFPage pagePDF1 = pdf1.getPage(i);
		            if(pagePDF1 == null)
		            	continue;
					BufferedImage pageImgPDF1 = convertPage(pagePDF1);
					
					
					// highlight the differences
					BufferedImage diffimg = visualiseDifferences(pageImgPDF1, entryHolders);
						
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
	 * Highlight the differences of the pdf in the givenimage
	 * 
	 * @param pageImgPDF1
	 * @param entryHolders
	 * @throws IOException 
	 */
	private BufferedImage visualiseDifferences(BufferedImage pageImgPDF1, Set<PDFEntryHolder> entryHolders) throws IOException
	{
		// deep image copy
		BufferedImage diffImg = deepCopy(pageImgPDF1);
		
		Graphics g = diffImg.getGraphics();
		g.setColor(Color.RED);
		for (PDFEntryHolder pdfEntryHolder : entryHolders) 
		{
			// check if there are any differences
			if(!pdfEntryHolder.isDifferent())
				continue;
			
			int entryX = (int)(pdfEntryHolder.getX() * PDFVisualComparator.IMAGE_SCALER);
			int entryY = (int)(pdfEntryHolder.getY() * PDFVisualComparator.IMAGE_SCALER);
			int entryWidth = (int)(pdfEntryHolder.getWidth() * PDFVisualComparator.IMAGE_SCALER);
			int entryHeight = (int)(pdfEntryHolder.getHeight() * PDFVisualComparator.IMAGE_SCALER);
			
			// draw red rect around images
			if(pdfEntryHolder instanceof PDFImageHolder)
				g.drawRect(entryX, entryY, entryWidth, entryHeight);
			// and underline text
			else if(pdfEntryHolder instanceof PDFTextHolder)
				g.drawLine(entryX, entryY+entryHeight, entryX+entryWidth, entryY+entryHeight);
				
		}		
		diffImg.flush();
		return diffImg;
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
	private BufferedImage convertPage(PDFPage page) throws IllegalArgumentException {

			// get the width and height for the doc at the default zoom
			Rectangle rect = new Rectangle(0, 0, (int) page.getBBox().getWidth(), (int) page.getBBox().getHeight());
			
			int pageWidth = (int) (rect.width * PDFVisualComparator.IMAGE_SCALER);
			int pageHeight = (int) (rect.height * PDFVisualComparator.IMAGE_SCALER);
			
			BufferedImage bImg = (BufferedImage) page.getImage(pageWidth, pageHeight, // width & height
					rect, // clip rect
					null, // null for the ImageObserver
					true, // fill background with white
					true // block until drawing is done
					);
			
			return bImg;
	}
}
