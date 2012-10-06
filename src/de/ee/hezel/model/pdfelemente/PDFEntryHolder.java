/*
* PDFEntryHolder
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
package de.ee.hezel.model.pdfelemente;

import java.awt.geom.Rectangle2D;

import de.ee.hezel.model.PDFPageHolder;

/**
 * PDFEntryHolder holding meta data for a
 * single element of the PDF document.
 * 
 * Containing:
 * - size of the element
 * - position of the element
 * 
 * Ideally every element exists in both PDF documents.
 * If this is not the case "isDifferent" would become true. 
 * 
 * @author hezeln
 *
 */
public class PDFEntryHolder {
	
	protected double x;
	protected double y;
	protected double width;
	protected double height;
	
	protected PDFPageHolder pdfPageHolder;
	
	protected boolean isDifferent;

	public PDFEntryHolder(double x, double y, double width, double height)
	{
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.pdfPageHolder = null;
		this.isDifferent = false;
	}
	
	public double getX() {
		return x;
	}

	public void setX(double x) {
		this.x = x;
	}

	public double getY() {
		return y;
	}

	public void setY(double y) {
		this.y = y;
	}

	public double getWidth() {
		return width;
	}

	public void setWidth(double width) {
		this.width = width;
	}

	public double getHeight() {
		return height;
	}

	public void setHeight(double height) {
		this.height = height;
	}

	public Rectangle2D getRectangle()
	{
		 return new Rectangle2D.Float((float)x, (float)y, (float)width, (float)height);
	}
	
	/**
	 * on which page does this element exist
	 * 
	 * @return pageNumber
	 */
	public PDFPageHolder getPDFPageHolder() {
		return pdfPageHolder;
	}

	/**
	 * set the page number for this element
	 * 
	 * @param pageNumber
	 */
	public void setPDFPageHolder(PDFPageHolder pdfPageHolder) {
		this.pdfPageHolder = pdfPageHolder;
	}
	
	/**
	 * is this element different between the 
	 * compared pdf documents
	 * 
	 * @return
	 */
	public boolean isDifferent() {
		return isDifferent;
	}

	/**
	 * the analyse process can define if this
	 * element is different between the compared pdfs.
	 * 
	 * @param isDifferent
	 */
	public void setDifferent(boolean isDifferent) {
		this.isDifferent = isDifferent;
	}
}
