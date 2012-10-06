/*
* PDFStructureComparator
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
import java.text.DecimalFormat;

import de.ee.hezel.logger.ICompareLogger;
import de.ee.hezel.model.PDFHolder;
import de.ee.hezel.model.PDFInfoHolder;
import de.ee.hezel.model.PDFPageHolder;
import de.ee.hezel.model.pdfelemente.PDFEntryHolder;
import de.ee.hezel.model.pdfelemente.PDFTextHolder;

/**
 * Compare the pdf structures and find differences
 *  
 * With this method small changes (approx. > 10% difference per pdf element)
 * gets found and saves. 
 * 
 * If a "SIMPLE" comparison is desired. The horizontal position
 * of the element gets ignored.
 * 
 * @author hezeln
 *
 */
public class PDFStructureComparator extends AbstractPDFCompare {

	private final boolean isSimpleComparison;
	private PDFInfoHolder pdfInfoHolder;
	
	public PDFStructureComparator(boolean isSimpleComparison, PDFInfoHolder pdfih)
	{
		this.isSimpleComparison = isSimpleComparison;
		pdfInfoHolder = pdfih;
	}
	
	public PDFStructureComparator(boolean isSimpleComparison, ICompareLogger diffLog, PDFInfoHolder pdfih)
	{
		this(isSimpleComparison, pdfih);
		setDifferenceLogger(diffLog);
	}
	
	/**
	 * Compare the pdf structures and find differences
	 *  
	 * With this method small changes (approx. > 10% difference per pdf element)
	 * gets found and saves. 
	 * 
	 * If a "SIMPLE" comparison is desired. The horizontal position
	 * of the element gets ignored.
	 * 
	 * @param pdfHolders
	 */
	public void compare() 
	{
		// already found differences (e.g. different page count)
		if(!pdfInfoHolder.isDifferent())
		{
			PDFHolder pdfHolder1 = pdfInfoHolder.getPDFStructure1();
			PDFHolder pdfHolder2 = pdfInfoHolder.getPDFStructure2();
	
			// missing the counter part
			if(pdfHolder1 == null || pdfHolder2 == null)
			{
				pdfInfoHolder.setDifferent(true);
				return;
			}
			
			//compare the structures, in both directions
			comparePDFHolder(pdfHolder1, pdfHolder2);			
			comparePDFHolder(pdfHolder2, pdfHolder1);
			
			// check if a difference was found on one of the pages.
			// if so mark the entire pdf as different
			pdfInfoHolder.checkDifference();
		}
	}
	
	/**
	 * compare the structure of the given pdf documents
	 * 
	 * @param pdfHolder1
	 * @param pdfHolder2
	 */
	private void comparePDFHolder(PDFHolder pdfHolder1, PDFHolder pdfHolder2)
	{
		for (PDFPageHolder pdfPageHolder1 : pdfHolder1.getPageHolders()) {
			
			// get the same page from the other pdf document
			PDFPageHolder pdfPageHolder2 = pdfHolder2.getPageHolder(pdfPageHolder1.getPageNumber());
			if (pdfPageHolder2 == null) {
			    diff.log(pdfInfoHolder.getFilename()+": page " + pdfPageHolder1.getPageNumber() + " missing in other pdf");
			    return;
			}
			
			// run thru all structure elements for this page
			for (PDFEntryHolder pdfEntryHolder1 : pdfPageHolder1.getElements()) {
				
				// try to find the same element at the same page of the other pdf 
				PDFEntryHolder pdfEntryHolder2 = findEntryHolder(pdfEntryHolder1, pdfPageHolder2);
				
				// no valid element found, assume difference
				if(pdfEntryHolder2 == null)
				{
					pdfEntryHolder1.setDifferent(true);
					DecimalFormat df = new DecimalFormat( "####.###" );
					if(pdfEntryHolder1 instanceof PDFTextHolder)
					{
						PDFTextHolder th = (PDFTextHolder)pdfEntryHolder1;
						diff.log(pdfInfoHolder.getFilename()+": Could not find smiliar text \""+th.getText()+"\" on page " 
								+ (pdfPageHolder1.getPageNumber()+1) + " at position " + df.format(th.getX())+" | " + df.format(th.getY()) 
								+ " with size " + df.format(th.getWidth()) + " width and " + df.format(th.getHeight()) + " height");
					}
					else
					{
						diff.log(pdfInfoHolder.getFilename()+": Could not find smiliar image on page " + (pdfPageHolder1.getPageNumber()+1) 
								+ " at position " + df.format(pdfEntryHolder1.getX())+" | " + df.format(pdfEntryHolder1.getY()) + " with size " 
								+ df.format(pdfEntryHolder1.getWidth()) + " width and " + df.format(pdfEntryHolder1.getHeight()) + " height");
					}
				}
			}
		}
	}
	
	/**
	 * Try to find an element in the given page which is as similar as 
	 * possible to the given element
	 * 
	 * similar means:
	 * 		- same element type (e.g. image, text)
	 * 		- same text (if text element)
	 * 		- same size and position 
	 * 			* for images: both images cover 99%  of each other
	 * 			* for text: 85-65% (depending on the text)
	 * 			* for text and SIMPLE mode: 55-65%
	 * 
	 * @param pdfEntryHolderSearch
	 * @param pdfPageHolder
	 * @return
	 */
	private PDFEntryHolder findEntryHolder(PDFEntryHolder pdfEntryHolderSearch, PDFPageHolder pdfPageHolder)
	{
		boolean bestIsMalformed = false;
		float bestCoverage = 0f;
		PDFEntryHolder bestEntryHolder = null;
		
		for (PDFEntryHolder pdfEntryHolder : pdfPageHolder.getElements())
		{
			if(pdfEntryHolderSearch.getClass() != pdfEntryHolder.getClass())
				continue;
			
			// check if one of the elements is broken
			boolean isMalformed = isMalformedTextHolder(pdfEntryHolderSearch, pdfEntryHolder);

			// how much does the elements cover each other
			float areaCoverage = calcAreaCoverage(pdfEntryHolderSearch, pdfEntryHolder, isMalformed);
			if(pdfEntryHolderSearch instanceof PDFTextHolder)
			{
				String searchText = ((PDFTextHolder)pdfEntryHolderSearch).getText();
				String text = ((PDFTextHolder)pdfEntryHolder).getText();
				
				// is the text the same
				if(!searchText.equalsIgnoreCase(text))
					areaCoverage = 0.0f;
			}	
			
			// check if the element is cut by the page borders
			if(horziontalPositionFromElementInPage(pdfEntryHolder, pdfPageHolder) !=
					horziontalPositionFromElementInPage(pdfEntryHolderSearch, pdfPageHolder))
				areaCoverage = 0.0f;
			
			// remember the best element 
			if(bestCoverage < areaCoverage)
			{
				bestCoverage = areaCoverage;
				bestEntryHolder = pdfEntryHolder;
				bestIsMalformed = isMalformed;
			}
		}
		
		// check if the result is within the requirements
		if(!hasSufficientSimilarity(bestCoverage, bestEntryHolder, bestIsMalformed))
			bestEntryHolder = null;
			
		return bestEntryHolder;
	}
	
	/**
	 * Calculate the horizontal position of the element.
	 * If the elements hits the border on the lefts side of the page
	 * -1 gets returned. If the elements is exact in the middle 0 gets
	 * return and 1 if the elements cuts the border on the right side
	 *  
	 * @param pdfEntryHolder
	 * @param pdfPageHolder
	 * @return
	 */
	private int horziontalPositionFromElementInPage(PDFEntryHolder pdfEntryHolder, PDFPageHolder pdfPageHolder)
	{
		if(pdfEntryHolder.getX() < 0)
			return -1;
		else if(pdfEntryHolder.getX()+pdfEntryHolder.getWidth() > pdfPageHolder.getPageWidth())
			return 1;
		
		return 0;
	}
	
	/**
	 * Text element are sometimes broken. There height is 0.
	 * If the vertical position and the text is the same to the element on the other pdf
	 * use their height instead.
	 * 
	 * @param entryHolder1
	 * @param entryHolder2
	 * @return
	 */
	private boolean isMalformedTextHolder(PDFEntryHolder entryHolder1, PDFEntryHolder entryHolder2) {
		if (entryHolder1 instanceof PDFTextHolder) {
			// if the text is the same, it might be the same element
			if (!((PDFTextHolder) entryHolder1).getText().equalsIgnoreCase(((PDFTextHolder) entryHolder2).getText()))
				return false;

			if ((entryHolder1.getHeight() <= 1 && entryHolder2.getHeight() > 1)
					|| (entryHolder2.getHeight() <= 1 && entryHolder1.getHeight() > 1))
				return true;
		}
		return false;
	}

	
	/**
	 * SIMPLE Mode:
	 * Image should have a coverage of 99%. Text only 65% 
 	 * or 55% if the text element was broken.
	 * 
	 * 
	 * STRUCTURAL Mode:
	 * Images should have a coverage of 99%. Text which is
	 * longer then 5 letters need 85%.
	 * And for every letter less, it gets reduced by 2%.
	 * 
	 * If the element was broken, another 10% gets reduced.
	 * 
	 * @param coverage
	 * @param entryHolder
	 * @return
	 */
	private boolean hasSufficientSimilarity(float coverage, PDFEntryHolder entryHolder, boolean isMalformed)
	{
		float minCoverage = 0.99f;
		
		// text does have difference min coverage depending on their length
		if(entryHolder instanceof PDFTextHolder)
		{
			// SIMPLE mode
			if(isSimpleComparison)
			{
				// broken text element get 10% extra
				float reduceCoverage = (isMalformed) ? 0.1f : 0.0f;
								
				minCoverage = 0.65f - reduceCoverage;
			}
			else
			{
				// STRUCTURAL mode
				String text = ((PDFTextHolder)entryHolder).getText();
		
				// for each char less 5 the coverage gets reduced by 2%
				// e.g. 1 letter text = 10% reduction
				// 4 letter ext = 2 %
				float reduceCoverage = (float)(10 - text.length() * 2) / 100;	
				
				// broken text element get 10% extra
				if(isMalformed)
					reduceCoverage += 0.1f; 
				
				if(reduceCoverage < 0)
					reduceCoverage = 0;
					
				minCoverage = 0.85f - reduceCoverage;
			}
		}
			
		return (coverage > minCoverage) ? true : false;
	}
	
	/**
	 * SIMPLE mode
	 * Calculate how strong the area of the entryholder cover
	 * each other. Consider only the vertical position and size.
	 * 
	 * STRUCTURAL mode
	 * Calculate how strong the area of the entryholder cover
	 * each other.
	 * 
	 * @param entryHolder1
	 * @param entryHolder2
	 * @return
	 */
	private float calcAreaCoverage(PDFEntryHolder entryHolder1, PDFEntryHolder entryHolder2, boolean isMalformed)
	{
		double eh1_height = entryHolder1.getHeight();
		double eh2_height = entryHolder2.getHeight();
		
		double eh1_y = entryHolder1.getY();
		double eh2_y = entryHolder2.getY();
		
		// repair is possible the height of broken text elements
		if(isMalformed)
		{
			// this is quite optimistic strategy. but if both elements
			// lie away from each other, thos additional pixels will not help them
			if(entryHolder1.getHeight() <= 1 && entryHolder2.getHeight() > 1)
			{
				eh1_height = entryHolder2.getHeight();
				eh1_y = eh1_y-(eh1_height-entryHolder1.getHeight());
			}
			else if(entryHolder1.getHeight() > 1 && entryHolder2.getHeight() <= 1)
			{
				eh2_height = entryHolder1.getHeight();
				eh2_y = eh2_y-(eh2_height-entryHolder2.getHeight());
			}
		}
		
		double coverageRect1 = 0, coverageRect2 = 0;
		if(isSimpleComparison)
		{
			/**
			 * minimal coverage of the overlapping area in vertical direction
			 * 
			 * 	                minFromX-----
			 * ---------------  |           |
			 * |             |  |           |
			 * minToX---------  |           |
			 *                  |------------
			 */		
			double coveredHeight = 0;
			if(eh1_y < eh2_y)
				coveredHeight = eh1_height - (eh2_y - eh1_y);
			else if(eh1_y > eh2_y)
				coveredHeight = eh2_height - (eh1_y - eh2_y);
			else if(eh1_y == eh2_y)
				coveredHeight = Math.min(eh1_height, eh2_height);
			
			if(coveredHeight <= 0)
				return 0.0f;
		
			// how strong is the coverage for each entry holder
			coverageRect1 = coveredHeight / eh1_height;
			coverageRect2 = coveredHeight / eh2_height;

		}
		else
		{
			Rectangle2D rect1 = new Rectangle2D.Double(entryHolder1.getX(), eh1_y, entryHolder1.getWidth(), eh1_height);
			Rectangle2D rect2 = new Rectangle2D.Double(entryHolder2.getX(), eh2_y, entryHolder2.getWidth(), eh2_height);
			
			if(!rect1.intersects(rect2))
				return 0.0f;
			
			// calc the overlapping area
			Rectangle2D result = new Rectangle2D.Float();
			Rectangle2D.intersect(rect1, rect2, result);
			
			// compare covered area with original area
			double resultArea = result.getWidth()*result.getHeight();
			coverageRect1 = resultArea / (rect1.getWidth()*rect1.getHeight());
			coverageRect2 = resultArea / (rect2.getWidth()*rect2.getHeight());
		}
		
		// We use always the not so good coverage.
		// because a small element can lie in a bigger one and 
		// would have a coverage of 100% 
		return (float)Math.min(coverageRect1, coverageRect2);
	}
}
