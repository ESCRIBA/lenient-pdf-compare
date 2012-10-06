/*
* PDFHolder
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

import java.util.Collection;
import java.util.HashMap;

/**
 * meta data for the pdf document
 * 
 * @author hezeln
 *
 */
public class PDFHolder {
	
	private HashMap<Integer, PDFPageHolder> pageHolders;
	private int numberOfPages;
	private boolean isDifferent;
	
	public PDFHolder(int numberOfPages)
	{
		this.pageHolders = new HashMap<Integer, PDFPageHolder>();
		this.isDifferent = false;
		this.numberOfPages = numberOfPages;
	}
		
	public void addPageHolders(PDFPageHolder pageHolders) {
		this.pageHolders.put(pageHolders.getPageNumber(), pageHolders);
	}
	
	public void removePageHolder(int pageNumber)
	{
		this.pageHolders.remove(new Integer(pageNumber));
	}
	
	public PDFPageHolder getPageHolder(int pageNumber)
	{
		return this.pageHolders.get(new Integer(pageNumber));
	}
	
	public Collection<PDFPageHolder> getPageHolders()
	{
		return this.pageHolders.values();
	}
	


	public boolean isDifferent() {
		return isDifferent;
	}

	public void setDifferent(boolean isDifferent) {
		this.isDifferent = isDifferent;
	}
	
	public boolean checkDifference()
	{
		for (PDFPageHolder elements : pageHolders.values()) {
			if(elements.checkDifference())
			{
				this.isDifferent = true;
			}
		}
		
		return this.isDifferent;
	}
}
