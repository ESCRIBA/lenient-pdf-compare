/*
* PDFTextHolder
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

/**
 * @author hezeln
 *
 */
public class PDFTextHolder extends PDFEntryHolder {

	private String text;
	
	public PDFTextHolder(double x, double y, double width, double height) {
		super(x, y, width, height);
	}
	
	public PDFTextHolder(double x, double y, double width, double height, String text) {
		super(x, y, width, height);
		
		this.text = text;
	}
	
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
}
