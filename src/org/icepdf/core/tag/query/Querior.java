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
package org.icepdf.core.tag.query;

import org.icepdf.core.tag.TagState;
import org.icepdf.core.tag.TaggedImage;
import org.icepdf.core.tag.TaggedDocument;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author mcollette
 * @since 4.0
 */
public class Querior {
    public static Expression parse(String[] unparsedQuery) throws ParseException {
        int len = (unparsedQuery == null) ? 0 : unparsedQuery.length;
        Object[] parsedQuery = new Object[len];
        for (int i = 0; i < len; i++) {
            // Operators
            if (unparsedQuery[i].equals("img:and")) {
                Operator op = new And();
                op.setScope(Operator.SCOPE_IMAGE);
                parsedQuery[i] = op;
            }
            else if (unparsedQuery[i].equals("tag:and")) {
                Operator op = new And();
                op.setScope(Operator.SCOPE_TAG);
                parsedQuery[i] = op;
            }
            else if (unparsedQuery[i].equals("img:or")) {
                Operator op = new Or();
                op.setScope(Operator.SCOPE_IMAGE);
                parsedQuery[i] = op;
            }
            else if (unparsedQuery[i].equals("tag:or")) {
                Operator op = new Or();
                op.setScope(Operator.SCOPE_TAG);
                parsedQuery[i] = op;
            }
            else if (unparsedQuery[i].equals("img:not")) {
                Operator op = new Not();
                op.setScope(Operator.SCOPE_IMAGE);
                parsedQuery[i] = op;
            }
            else if (unparsedQuery[i].equals("tag:not")) {
                Operator op = new Not();
                op.setScope(Operator.SCOPE_TAG);
                parsedQuery[i] = op;
            }
            // Functions
            else if (unparsedQuery[i].equals("substring"))
                parsedQuery[i] = new Substring();
            // Literal argument
            else
                parsedQuery[i] = unparsedQuery[i];
        }
        int numTokensParsed = rpnParse(parsedQuery, 0);
        if (numTokensParsed != parsedQuery.length)
            throw new ParseException("Number of tokens given: " + parsedQuery.length + ", number of tokens parsed: " + numTokensParsed);
String desc = ((Expression) parsedQuery[0]).describe(0);
System.out.println(desc);
        return (Expression) parsedQuery[0];
    }

    /*

Expression
    public int getArgumentCount();
Operator
    public void setChildExpressions(Expression[] children) {
Function
    public void setArguments(String[] arguments) {

    */
    private static int rpnParse(Object[] parsedQuery, int position) throws ParseException {
        Object curr = parsedQuery[position];
        int tokens = 1;
        if (curr instanceof Expression) {
            int numArgs = ((Expression) curr).getArgumentCount();
            List args = new ArrayList(numArgs);
            for (int a = 0; a < numArgs; a++) {
                int currArgIndex = position + tokens;
                if ((currArgIndex) >= parsedQuery.length) {
                    throw new ParseException("Token at position " + position + " has argument " + (a+1) + " of " + numArgs + " which is calculated to be at position " + currArgIndex + ", which beyond the parsed list of query tokens");
                }
                tokens += rpnParse(parsedQuery, currArgIndex);
                args.add(new Integer(currArgIndex));
            }
            if (curr instanceof Operator) {
                Operator op = (Operator) curr;
                Expression[] eargs = new Expression[numArgs];
                for(int a = 0; a < numArgs; a++) {
                    Integer index = (Integer) args.get(a);
                    Object arg = parsedQuery[index.intValue()];
                    if (!(arg instanceof Expression))
                        throw new ParseException("Token at position " + position + " has argument " + (a+1) + " of " + numArgs + ", calculated to be at position " + index + ", which is expected to be an expression token, but is instead a literal string: " + arg);
                    eargs[a] = (Expression) arg;
                }
                op.setChildExpressions(eargs);
            }
            else if (curr instanceof Function) {
                Function func = (Function) curr;
                String[] sargs = new String[numArgs];
                for(int a = 0; a < numArgs; a++) {
                    Integer index = (Integer) args.get(a);
                    Object arg = parsedQuery[index.intValue()];
                    if (arg instanceof Expression)
                        throw new ParseException("Token at position " + position + " has argument " + (a+1) + " of " + numArgs + ", calculated to be at position " + index + ", which is expected to be literal string, but is instead an expression token: " + arg);
                    sargs[a] = arg.toString();
                }
                func.setArguments(sargs);
            }
        }
        return tokens;
    }

    public static List search(TagState state, Expression queryRoot) {
        int docCount = 0;
        int imgCount = 0;
        int tagCount = 0;
        //TODO Add support for scope
        List results = new ArrayList(32);

        List docs = state.getDocuments();
        for (Iterator docIt = docs.iterator(); docIt.hasNext();) {
            docCount++;
            DocumentResult docRes = null;

            TaggedDocument td = (TaggedDocument) docIt.next();
            List images = td.getImages();
            for (Iterator imIt = images.iterator(); imIt.hasNext();) {
                imgCount++;
                boolean imageInResults = false;

                TaggedImage ti = (TaggedImage) imIt.next();
                List tags = ti.getTags();
                for (Iterator tagIt = tags.iterator(); tagIt.hasNext();) {
                    tagCount++;
                    String tag = (String) tagIt.next();
                    if (queryRoot.matches(td, ti, tag)) {
                        if (docRes == null) {
                            docRes = new DocumentResult(td, ti);
                            imageInResults = true;
                            results.add(docRes);
                        }
                        else if (!imageInResults) {
                            docRes.addImage(ti);
                            imageInResults = true;
                        }
                    }
                }
            }
        }
System.out.println("Searched " + docCount + " documents, " + imgCount + " images, " + tagCount + " tags.  Found " + results.size() + " matching documents.");
        return results;
    }
}
