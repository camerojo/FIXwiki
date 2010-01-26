/*
 * Copyright (c) 2009 Cameron Edge Pty Ltd. All rights reserved.
 * Reproduction in whole or in part in any form or medium without express
 * written permission of Cameron Edge Pty Ltd is strictly prohibited.
 */

package com.cameronedge.fixrepo;

import com.cameronedge.fixwiki.LinkDetector;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * @author John Cameron
 */
public class Util {
  private static Set<Character> wikiFormattingChars = new HashSet<Character>();

  static {
    wikiFormattingChars.add(' ');
    wikiFormattingChars.add('*');
    wikiFormattingChars.add('#');
    wikiFormattingChars.add(';');
    wikiFormattingChars.add(':');
    wikiFormattingChars.add('=');
    wikiFormattingChars.add('{');
    wikiFormattingChars.add('|');
    wikiFormattingChars.add('!');
  }

  public static String camelCase(String s) {
    StringBuffer ret = new StringBuffer();
    StringTokenizer stok = new StringTokenizer(s);
    while (stok.hasMoreTokens()) {
      String token = stok.nextToken();

      if (token.length() > 0) {
        //Capitalize first letter.
        char firstChar = token.charAt(0);
        if (Character.isLowerCase(firstChar)) {
          firstChar = Character.toUpperCase(firstChar);
        }

        ret.append(firstChar);
        if (token.length() > 1) {
          ret.append(token.substring(1));
        }
      }
    }
    return ret.toString();
  }

  public static String cleanText(String s) {
    s = s.trim();
    int len = s.length();
    StringBuffer cleanValue = new StringBuffer(len);
    for (int i = 0; i < len; i++) {
      char ch = s.charAt(i);
      String convertedCh = String.valueOf(ch);

      boolean handled = true;

      switch (ch) {
        case '�':
        case '�':
          convertedCh = String.valueOf('"');
          break;

        case '�':
        case '�':
          convertedCh = String.valueOf('\'');
          break;

        case 8218:
          convertedCh = String.valueOf(',');
          break;

        case 8594: //Right arrow
          convertedCh = "->";
          break;

        case 65533:
        case '�':
        case '�':
          convertedCh = String.valueOf('-');
          break;

        case 8195: //Funny space which messes up Java parsing.
          convertedCh = String.valueOf(' ');
          break;

        case '�':
          //We leave these alone for later processing.
          break;

        default:
          handled = false;
      }

      if (ch > 255 && !handled) {
        System.out.println("WARNING: Unhandled non ASCII character " + ch + "(" + (int) ch + ")");
      }

      cleanValue.append(convertedCh);
    }

    return cleanValue.toString();
  }

  public static String computeEnumName(String description) {
    //Rule is take everything up to first non alphanum or space - then trim.
    //Special case for '-' not preceeded by space, like Non-
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < description.length(); i++) {
      char c = description.charAt(i);
      if (Character.isLetterOrDigit(c) || c == ' ' || c == '_') {
        sb.append(c);
      } else {
        //Just throw away junk before we get our first good character.
        if (sb.length() != 0) {
          //Will accept '-' if not preceeded by a space.
          if (c == '-') {
            if (description.charAt(i - 1) == ' ') {
              break;
            }
            sb.append(c);
          } else {
            break;
          }
        }
      }
    }

    String enumName = sb.toString().trim();

    //Now convert to camel case word, removing any non alphanumeric characters
    //Replace any '_'or '-' by space
    enumName = enumName.replace('_', ' ');
    enumName = enumName.replace('-', ' ');

    //Convert to camel case.
    enumName = Util.camelCase(enumName);

    return enumName;
  }

  public static String computeFPLTitle(String userTitle) {
    return "FPL:" + userTitle;
  }

  public static String computeValueTitle(String fieldName, String enumValue, String enumName) {
    return fieldName + "/" + enumValue + " " + enumName;
  }

  private static String copyUntil(Reader reader, char c) throws IOException {
    StringBuffer sb = new StringBuffer();
    String retVal;

    int ch;
    do {
      ch = reader.read();
      if (ch >= 0 && ch != c) {
        sb.append((char) ch);
      }
    } while (ch != -1 && ch != c);

    if (ch == -1 && sb.length() == 0) {
      //No data read and end of stream detected.
      retVal = null;
    } else {
      retVal = sb.toString();
    }

    return retVal;
  }

  public static String formatDescription(String value, LinkDetector linkDetector) {
    //Turn all whole words which match field or messages names into links by surrounding them with [[]]
    String valueWithLinks = linkDetector == null ? value : linkDetector.convert(value);

    String formatted = valueWithLinks;

    if (valueWithLinks != null && valueWithLinks.length() > 0) {
      LineNumberReader reader = new LineNumberReader(new StringReader(valueWithLinks));

      StringBuffer result = new StringBuffer();
      String s;
      try {
        while ((s = reader.readLine()) != null) {
          s = s.trim();

          if (s.length() > 0) {

            s = Util.cleanText(s);

            char firstChar = s.charAt(0);
            if (firstChar <= 255) {
              //Normal ASCII character.
              //Escape it if it is some Wiki formatting character.
              if (wikiFormattingChars.contains(firstChar)) {
                result.append("<nowiki>");
                result.append(firstChar);
                result.append("</nowiki>");
                if (s.length() > 1) {
                  result.append(s.substring(1));
                }
              } else {
                result.append(s);
              }
            } else {
              //Special character triggers some special processing.
              if (firstChar == '�') {
                //Treat this as a list - translate to standard Wiki list character '*'.
                result.append('*');
                if (s.length() > 1) {
                  result.append(s.substring(1));
                }
              } else {
                result.append(s);
              }
            }
          }

          //New lines convert to double new lines, forcing a Wiki line break
          result.append("\n\n");
        }
      } catch (IOException ex) {
        //Cannot happen processing a String
      }

      formatted = result.toString();

      //Having bar characters anywhere in the string is asking for trouble! Bar is used as delimiter is passing
      //parameteres to Wiki templates.
      formatted = formatted.replace('|', ' ');
    }
    return formatted;
  }

  /**
   * Parses the contents of a stream containing the pasted text from a copied MS Word table.
   * <p/>
   * Returns a list where each entry in the list corresponds to a row of the table. Each list entry is
   * an array of Strings - one array element per column in the table.
   *
   * @param pastedTableStr String containing pasted table text
   * @param nColumns       Number of columns in table
   * @return List representing rows of table. Each row is an array corresponding to the columns in the table.
   */
  public static List<String[]> parseMSWordTable(String pastedTableStr, int nColumns) throws IOException {
    boolean doubleTabs = false;

    Reader pastedTable = new StringReader(pastedTableStr);

    List<String[]> parsedTable = new ArrayList<String[]>();

    //Eat very first tab - delimiting end of non existent previous row.
    String s = copyUntil(pastedTable, '\t');
    if (s.length() > 0) {
      System.out.println("Unexpected text found before start of table: " + s);
    }

    //Loop for each row
    while (true) {
      String[] entry = new String[nColumns];

      //Loop for each column.
      for (int i = 0; i < nColumns; i++) {
        entry[i] = copyUntil(pastedTable, '\t');

        if (entry[i] == null) {
          break;
        }

        if (doubleTabs) {
          //Eat next char unless very last column
          if (i != nColumns - 1) {
            int intch = pastedTable.read();
            if (intch == -1) {
              break;
            }
            char ch = (char) intch;
            if (ch != '\t') {
              System.out.println("Expected double tab, found tab followed by " + (int) ch);
            }
          }
        }
      }

      //No more rows
      if (entry[0] == null) {
        break;
      }

      parsedTable.add(entry);
    }
    return parsedTable;
  }

  public static void writeWikiTable(Writer writer, List<String[]> table, LinkDetector linkDetector) {
    PrintWriter wikiWriter = new PrintWriter(writer);
    wikiWriter.println("\n{|border=\"1\"");
    for (String[] columns : table) {

      wikiWriter.println("\n|-");
      for (String column : columns) {
        wikiWriter.print("\n|");

        //Convert text to contain links.
        wikiWriter.print(formatDescription(column, linkDetector));
      }
    }
    wikiWriter.println("\n|}");
  }
}
