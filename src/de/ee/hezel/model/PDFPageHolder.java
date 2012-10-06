/*
* PDFPageHolder
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
package de.ee.hezel.model;

import java.util.HashSet;
import java.util.Set;

import de.ee.hezel.model.pdfelemente.PDFEntryHolder;

/**
 * meta data holder for a single pdf page
 * 
 * @author hezeln
 *
 */
public class PDFPageHolder {

	private int pageNumber;
	private float pageWidth;
	private float pageHeight;
	private boolean isDifferent;
	
	private Set<PDFEntryHolder> pdfElements;

	public PDFPageHolder(int pageNumber, float pageWidth, float pageHeight)
	{
		this.pageWidth = pageWidth;
		this.pageHeight = pageHeight;
		this.pageNumber = pageNumber;
		this.isDifferent = false;
		this.pdfElements = new HashSet<PDFEntryHolder>();
	}
	
	public int getPageNumber() {
		return pageNumber;
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber = pageNumber;
	}
	
	public float getPageWidth() {
		return pageWidth;
	}
	
	public float getPageHeight() {
		return pageHeight;
	}

	public boolean isDifferent() {
		return isDifferent;
	}

	public void setDifferent(boolean isDifferent) {
		this.isDifferent = isDifferent;
	}
	
	public boolean checkDifference()
	{
		for (PDFEntryHolder elements : pdfElements) {
			if(elements.isDifferent())
			{
				this.isDifferent = true;
			}
		}
		
		return this.isDifferent;
	}
	
	public void addElement(PDFEntryHolder e)
	{
		e.setPDFPageHolder(this);
		this.pdfElements.add(e);
	}
	
	public void removeElement(PDFEntryHolder e)
	{
		e.setPDFPageHolder(null);
		this.pdfElements.remove(e);
	}
	
	public Set<PDFEntryHolder> getElements()
	{
		return this.pdfElements;
	}
}
