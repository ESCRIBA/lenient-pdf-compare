/*
* PDFCorpusAnalyser
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

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.AndFileFilter;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.log4j.Logger;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.util.GraphicsRenderingHints;

import de.ee.hezel.logger.ICompareLogger;
import de.ee.hezel.model.PDFHolder;
import de.ee.hezel.model.PDFInfoHolder;
import de.ee.hezel.model.PDFPageHolder;
import de.ee.hezel.model.pdfelemente.PDFImageHolder;
import de.ee.hezel.model.pdfelemente.PDFTextHolder;

/**
 * This class analizes the structure of the pdf files
 * It created the PDFxxxHolder objects. They represent
 * the structure of the pdf
 * 
 * All data get extracted with the icePDF lib.
 * 
 * @author hezeln
 *
 */
public class PDFCorpusAnalyser extends AbstractPDFCompare {

	static Logger log = Logger.getLogger(PDFCorpusAnalyser.class.getName());
	
	// should be always 1
	static final float ZOOM = 1.0f; //2.77f;
	
	public PDFCorpusAnalyser(ICompareLogger diffLog)
	{
		setDifferenceLogger(diffLog);
	}
	
	/**
	 * Analize the the structure.
	 * Create a simple PDF structure.

	 * @param PDFInfoHolder contains the comparing pdfs
	 */
	public void analyse(PDFInfoHolder pdfInfoHolder)
	{
		Document pdfFile1 = new Document();
		Document pdfFile2 = new Document();
		
		try {
			// get pdf document
			pdfFile1.setFile(pdfInfoHolder.getPDFFile1().getAbsolutePath());
			pdfFile2.setFile(pdfInfoHolder.getPDFFile2().getAbsolutePath());
								
			// compare the number of pages
			if(pdfFile1.getNumberOfPages() != pdfFile2.getNumberOfPages())
			{
				pdfInfoHolder.setDifferent(true);
				diff.log(pdfInfoHolder.getFilename()+": Different amount of pages: "+pdfFile1.getNumberOfPages() +" to "+pdfFile2.getNumberOfPages());
			}
			
			// create a PDFHolder objects, which contains the entiere structure of the pdf documents
			pdfInfoHolder.setPDFStructure1(analysePDF(pdfFile1, pdfInfoHolder));
			pdfInfoHolder.setPDFStructure2(analysePDF(pdfFile2, pdfInfoHolder));				
		} catch (Exception e) {
			log.error(pdfInfoHolder.getFilename()+": "+e.getMessage(), e);
		} finally {
			pdfFile1.dispose();
			pdfFile2.dispose();
		}
	}

	/**
	 * Exctract the structure of the pdf document and save it in the pdfholder object
	 * 
	 * @param pdfFile
	 * @param pdfInfoHolder
	 * @return PDFHolder
	 */
	private PDFHolder analysePDF(Document pdfFile, PDFInfoHolder pdfInfoHolder)
	{
		PDFHolder pdfHolder = new PDFHolder(pdfFile.getNumberOfPages());
		
		// run thru all pages of this document
		int numPgs = pdfFile.getNumberOfPages();
		for (int pageNumber = 0; pageNumber < numPgs; pageNumber++) 
		{
			Page page = pdfFile.getPageTree().getPage(pageNumber, this);
			
			// dimension of the page
			PDimension sz = page.getSize(0.0f, ZOOM);
	        float pageWidth = sz.getWidth();
	        float pageHeight = sz.getHeight();
	        
	        // analize the structure of this page
	        PDFPageHolder pdfPageHolder = analysePDFPage(pdfInfoHolder, page, pageNumber, pageWidth, pageHeight);
			
			// release the page resource
			pdfFile.getPageTree().releasePage(pageNumber, this);
			
			// add the analized page to the pdf holder
			pdfHolder.addPageHolders(pdfPageHolder);
		}
		
		return pdfHolder;
	}
	
	/**
	 * Exctract the structure of the pdf page and save it in the PDFPageHolder object
	 * 
	 * @param page
	 * @param pageNumber
	 * @param pageWidth
	 * @param pageHeight
	 * @return
	 */
	private PDFPageHolder analysePDFPage(PDFInfoHolder pdfInfoHolder, Page page, int pageNumber, float pageWidth, float pageHeight)
	{
		// create a holder for this page
		PDFPageHolder pdfPageHolder =  new PDFPageHolder(pageNumber, pageWidth, pageHeight);
		
		// get all textelement for this page, from the icePDF lib
		PageText pt = page.getText();
					
		// create for all text elements, structure holder 
		for (LineText lt : pt.getPageLines()) 
		{
			for (WordText wt : lt.getWords()) 
			{
				// ignore white spaces
				if(wt.isWhiteSpace())
					continue;
				
				// get the dimension for this element
				Rectangle2D.Float rect = wt.getBounds();
				
				// pdf documents does have their coordinate origin in the lower left corner
				double y = (pageHeight-rect.y)-rect.height;
				
				// create the text holder
				PDFTextHolder pdfTextHolder = new PDFTextHolder(rect.x, y, rect.width, rect.height, wt.getText());
				pdfPageHolder.addElement(pdfTextHolder);
			}
		}
		
		// get all shapes for this page (e.g. images)
		Shapes shapes = page.getShapes(); //modified lib
		
		// create for all images structure holder
		if(shapes != null)
		{
			// get the image position and dimension (best results with zoom 1.0)
			Set<Rectangle2D.Double> imageBoundaries = shapes.getBoundingBoxesForImages((int)pageWidth, (int)pageHeight, GraphicsRenderingHints.PRINT, Page.BOUNDARY_CROPBOX, 0.0f, ZOOM, page);  //modifizierte Bibliothek
			
			// add all found image to the page holder
			for (Rectangle2D.Double rect : imageBoundaries) {
				PDFImageHolder pdfImageHolder = new PDFImageHolder(rect.x, rect.y, rect.width, rect.height);
				pdfPageHolder.addElement(pdfImageHolder);
			}
		}
		
		return pdfPageHolder;
	}
	
	/**
	 * Run thru the given folders and find pdf document which have the same name.
	 * For every pair, a PDFInfoHolder objects gets created.

	 * 
	 * @param path for the 1st directory
	 * @param path for the 2nd directory
	 * @param prefix 
	 * @return list of all pdf pairs
	 */
	public static Set<PDFInfoHolder> getSimplePDFInfoHolders(File pdfs1, File pdfs2, String prefix)
	{
		Set<PDFInfoHolder> pdfInfoHolders = new HashSet<PDFInfoHolder>();
		
		// are those valid pathes
		if(pdfs1 != null && pdfs2 != null && pdfs1.isDirectory() && pdfs2.isDirectory())
		{
			// create a filter to only get pdf files
		    List<FilenameFilter> filters = new ArrayList<FilenameFilter>();
		    if (null != prefix && prefix.length() > 0 ) {
	            PrefixFileFilter filter = new PrefixFileFilter(prefix, IOCase.SYSTEM);
	            filters.add(filter);
		    }
		    filters.add(new SuffixFileFilter(".pdf", IOCase.INSENSITIVE));
            FilenameFilter filter = new AndFileFilter(filters);
            
			//get all pdf file sin this folder
			String[] pdfDocuments1 = pdfs1.list(filter);
			String[] pdfDocuments2 = pdfs2.list(filter);
			
			for (int i=0; i<pdfDocuments1.length; i++) 
			{	
				// get the pdf file name
			    String pdfFilename1 = pdfDocuments1[i];
			    
			    // find the counterpart
			    boolean foundCounterPart = false;
			    for (String pdfFilename2 : pdfDocuments2) {
					if(pdfFilename1.equalsIgnoreCase(pdfFilename2))
					{
						// get the path for both pdf files
						File pdfFile1 = new File(pdfs1, pdfFilename1);
						File pdfFile2 = new File(pdfs2, pdfFilename1);
						
						// bind them together in a PDFInfoHolder objects
						PDFInfoHolder newPDFInfoHolder = new PDFInfoHolder(pdfFile1, pdfFile2);
						
						// no differences found yet
						if(newPDFInfoHolder.isDifferent() == false)
							pdfInfoHolders.add(newPDFInfoHolder);
						
						foundCounterPart = true;
					}
				}
			    
			    if(!foundCounterPart)
					log.info("Could not find "+pdfFilename1+" in "+pdfs2.getAbsolutePath());
			}
		}
		else
		{
			log.error("The path is not valid.");
		}		
		
		return pdfInfoHolders;
	}
}
