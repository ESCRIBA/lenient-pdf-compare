/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.fonts;

/**
 * CMap inteface.
 *
 * @since 3.0
 */
public interface CMap {

    /**
     * Maps the character id to an underlying unicode value if available.
     *
     * @param ch character code to find unicode value of.
     * @return unicode value of ch if available otherwise original ch is returned unaltered.
     */
    public char toSelector(char ch);

    public char toSelector(char ch, boolean isCFF);

    /**
     * Maps the character id to an underlying to unicode table. This method should
     * be called when looking for a unicode value for a CID.  This method differs
     * slightly from #toSelector in that it can return at String rather then a
     * single character code.
     *
     * @param ch character id to look for corresponding unicode values.
     * @return unicode value of specified character code.
     */
    public String toUnicode(char ch);

    /**
     * Determines if the cid should be interpreted as a one or two byte character.
     * Some CID fonts use the one byte notation but the two byte is the most
     * common bar far.
     *
     * @param cid character code to check length off
     * @return true if the cid should be considered as having a one byte length.
     */
    public boolean isOneByte(int cid);
}
