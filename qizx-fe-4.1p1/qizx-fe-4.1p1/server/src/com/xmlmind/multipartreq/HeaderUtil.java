/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
/*
 * Copyright (c) 2009 Pixware SARL. 
 *
 * Author: Hussein Shafie
 *
 * This file is part of the XMLmind MultipartRequest project.
 * For conditions of distribution and use, see the accompanying legal.txt file.
 */
package com.xmlmind.multipartreq;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

/**
 * Helpers related to headers.
 */
/*package*/ final class HeaderUtil {
    private HeaderUtil() {}

    /**
     * Splits the header in two parts: the name which is always in lower case
     * and the value for which the character case is preserved.
     *
     * @param headerLine header line to be split in two parts
     * @return an array of two strings or <code>null</code> if specified
     * header is malformed.
     */
    public static String[] splitHeader(String headerLine) {
        int pos = headerLine.indexOf(':');
        if (pos < 0) {
            return null;
        }

        String name = headerLine.substring(0, pos).trim().toLowerCase();
        String value = headerLine.substring(pos+1).trim();
        if (name.length() == 0 || value.length() == 0) {
            return null;
        }

        return new String[] { name, value };
    }

    /**
     * Splits the specified header value in an odd number of parts:
     * first part is the header value <i>per se</i>, optionally followed by
     * a number of parameter name/value pairs. 
     * <p>The name of a parameter is always in lower case. 
     * The character case is preserved in values.
     *
     * @param headerValue the value of a header
     * @return an array containing an odd number of strings or 
     * <code>null</code> if specified header value is malformed.
     */
    public static String[] splitHeaderValue(String headerValue) {
        // Collect tokens ---

        ArrayList list = new ArrayList();
        char quote = 0;
        StringBuffer token = null;

        int length = headerValue.length();
        for (int i = 0; i < length; ++i) {
            char c = headerValue.charAt(i);

            if (Character.isWhitespace(c) || 
                c == ',' || c == ';' || c == '=') {
                if (quote != 0) {
                    tokenAppend(token, c);
                } else {
                    if (token != null) {
                        list.add(token.toString());
                        token = null;
                    }

                    if (c == ',' || c == ';' || c == '=') {
                        list.add(Character.toString(c));
                    }
                }
            } else {
                if (token == null) {
                    token = new StringBuffer();

                    if (c == '\"') {
                        quote = c;
                    } else {
                        token.append(c);
                    }
                } else {
                    if (c == quote) {
                        if (!tokenAppend(token, c)) {
                            // The quote was not quoted: remove it from token.
                            token.setLength(token.length()-1);

                            list.add(token.toString());
                            token = null;
                            quote = 0;
                        }
                    } else {
                        if (quote == 0) {
                            token.append(c);
                        } else {
                            tokenAppend(token, c);
                        }
                    }
                }
            }
        }

        if (token != null) {
            list.add(token.toString());
        }

        // Check tokens ---

        int count = list.size();
        if (count < 1) {
            /*
            System.err.println("no tokens");
            */
            return null;
        }
        String[] parts = new String[count];
        count = 0;

        parts[count++] = (String) list.get(0);
        char state = ';';

        for (int i = 1; i < parts.length; ++i) {
            String part = (String) list.get(i);
            
            switch (state) {
            case ';':
                if (!";".equals(part) && !",".equals(part)) {
                    /*
                    System.err.println("expected ';' or ',', found '" + 
                                       part + "'");
                    */
                    return null;
                }
                state = 'n';
                break;
            case 'n':
                parts[count++] = part.toLowerCase();
                state = '=';
                break;
            case '=':
                if (!"=".equals(part)) {
                    /*
                    System.err.println("expected '=', found '" + part + "'");
                    */
                    return null;
                }
                state = 'v';
                break;
            case 'v':
                parts[count++] = part;
                state = ';';
                break;
            }
        }

        if (state != ';') {
            /*
            System.err.println("premature end of tokens (state='" + 
                               state + "')");
            */
            return null;
        }

        if (parts.length != count) {
            String[] parts2 = new String[count];
            System.arraycopy(parts, 0, parts2, 0, count);
            parts = parts2;
        }

        return parts;
    }

    private static boolean tokenAppend(StringBuffer token, char c) {
        int last = token.length()-1;
        if (last >= 0 && token.charAt(last) == '\\') {
            token.setCharAt(last, c);
            return true; // Means: was quoted.
        } else {
            token.append(c);
            return false;
        }
    }

    /*TEST_SPLIT_HEADER
    public static void main(String[] args) {
        for (int i = 0; i < args.length; ++i) {
            String arg = args[i];

            System.out.println("{" + arg + "}");

            String[] split = splitHeader(arg);
            if (split == null) {
                System.out.println("*** error: malformed header ***");
                continue;
            }

            System.out.print("\t{" + split[0] + "} :");

            split = splitHeaderValue(split[1]);
            if (split == null) {
                System.out.println("*** error: malformed header value ***");
                continue;
            }

            for (int j = 0; j < split.length; ++j) {
                System.out.print(" {" + split[j] + "}");
            }
            System.out.println();
        }
    }
    TEST_SPLIT_HEADER*/

    /**
     * Returns the value of specified parameter.
     *
     * @param headerValue the value of a header
     * @param paramName <em>case-insensitive</em> name of the parameter
     * @param defaultValue a default value of the parameter
     * @return value of specified parameter or <tt>defaultValue</tt> if such
     * parameter has not been specified.
     */
    public static String getParameter(String headerValue, String paramName,
                                      String defaultValue) {
        String[] parts = splitHeaderValue(headerValue);
        if (parts != null || parts.length > 1) {
            paramName = paramName.toLowerCase();

            for (int i = 1; i < parts.length; i += 2) {
                if (parts[i].equals(paramName)) {
                    return parts[i+1];
                }
            }
        }
        return defaultValue;
    }

    // -----------------------------------------------------------------------

    public static String decodeWords(String text) 
        throws IOException {
        if (text.indexOf("?=") < 0) {
            return text;
        }

        StringBuffer buffer = new StringBuffer();

        int from = 0;
        for (;;) {
            int pos1 = text.indexOf("=?", from);
            if (pos1 < 0) {
                break;
            }

            int pos2 = text.indexOf("?=", pos1+2);
            if (pos2 < 0) {
                break;
            }

            if (pos1 > from) {
                buffer.append(text.substring(from, pos1));
            }
            from = pos2+2;

            String word = text.substring(pos1+2, pos2);
            word = decodeWord(word);
            quoteWord(word, buffer);
        }

        if (from < text.length()) {
            buffer.append(text.substring(from));
        }

        return buffer.toString();
    }

    private static void quoteWord(String word, StringBuffer buffer) {
        boolean needQuotes = false;

        int length = word.length();
        for (int i = 0; i < length; ++i) {
            char c = word.charAt(i);

            if (c == '"' || Character.isWhitespace(c)) {
                needQuotes = true;
                break;
            }
        }

        if (!needQuotes) {
            buffer.append(word);
        } else {
            buffer.append('"');

            for (int i = 0; i < length; ++i) {
                char c = word.charAt(i);
                
                if (c == '"') {
                    buffer.append("\\\"");
                } else {
                    buffer.append(c);
                }
            }

            buffer.append('"');
        }
    }

    private static String decodeWord(String word) 
        throws IOException {
        int pos1 = word.indexOf('?');
        if (pos1 < 0) {
            throw new IOException("=?" + word + "?=, not an encoded-word");
        }

        int pos2 = word.indexOf('?', pos1+1);
        if (pos2 < 0) {
            throw new IOException("=?" + word + "?=, not an encoded-word");
        }

        String charset = word.substring(0, pos1);
        String method = word.substring(pos1+1, pos2).toLowerCase();
        String encoded = word.substring(pos2+1);
        
        byte[] bytes;
        if ("q".equals(method)) {
            bytes = decodeQ(encoded);
        } else if ("b".equals(method)) {
            bytes = decodeB(encoded);
        } else {
            throw new IOException("encoded-word =?" + word + 
                                  "?= uses an encoding or than B and Q");
        }
        if (bytes == null) {
            throw new IOException("cannode decode encoded-word =?" + 
                                  word + "?=");
        }

        try {
            return new String(bytes, charset);
        } catch (UnsupportedEncodingException e) {
            throw new IOException("encoded-word =?" + word + 
                                  "?= uses an unsupported charset");
        }
    }

    // --------------------------------------
    // decodeQ
    // --------------------------------------

    private static byte[] decodeQ(String word) {
        int length = word.length();
        byte[] bytes = new byte[length];
        int j = 0;

        for (int i = 0; i < length; ++i) {
            char c = word.charAt(i);

            switch (c) {
            case '_':
                bytes[j++] = (byte) ' ';
                break;
            case '=':
                if (i+2 < length) {
                    int b;
                    try {
                        b = Integer.parseInt(word.substring(i+1, i+3), 16);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                    bytes[j++] = (byte) b;
                    i += 2;
                } else {
                    return null;
                }
                break;
            default:
                bytes[j++] = (byte) c;
                break;
            }
        }

        if (j != bytes.length) {
            byte[] bytes2 = new byte[j];
            System.arraycopy(bytes, 0, bytes2, 0, j);
            bytes = bytes2;
        }
        return bytes;
    }

    // --------------------------------------
    // decodeB
    // --------------------------------------

    private static byte[] fromDigit = new byte[256];
    static {
        for (int i = 0; i < 256; ++i) 
            fromDigit[i] = -1;

        for (int i = 'A'; i <= 'Z'; ++i) 
            fromDigit[i] = (byte) (i - 'A');

        for (int i = 'a'; i <= 'z'; ++i) 
            fromDigit[i] = (byte) (26 + i - 'a');

        for (int i = '0'; i <= '9'; ++i) 
            fromDigit[i] = (byte) (52 + i - '0');

        fromDigit['+'] = 62;
        fromDigit['/'] = 63;
    }

    private static final int SPACE_OR_DIGIT1 = 0;
    private static final int DIGIT1          = 1;
    private static final int DIGIT2          = 2;
    private static final int DIGIT3_OR_EQUAL = 3;
    private static final int EQUAL           = 4;
    private static final int DIGIT4_OR_EQUAL = 5;
    private static final int END_OR_SPACE    = 6;

    private static byte[] decodeB(String s) {
        // Note that a zero-length base 64 string is valid.
        int length = s.length();
        byte[] b = new byte[3*(length/4) + 2];
        int j = 0;
        int state = SPACE_OR_DIGIT1;
        int bits = 0;
        int bitCount = 0;

        for (int i = 0 ; i < length; ++i) {
            char c = s.charAt(i);
            int value = 0;

            switch (state) {
            case SPACE_OR_DIGIT1:
                if (Character.isWhitespace(c)) {
                    // Same state.
                    continue;
                } else {
                    if (c > 255 || (value = fromDigit[c]) < 0)
                        return null;
                    state = DIGIT2;
                }
                break;
            case DIGIT1:
                if (c > 255 || (value = fromDigit[c]) < 0)
                    return null;
                state = DIGIT2;
                break;
            case DIGIT2:
                if (c > 255 || (value = fromDigit[c]) < 0)
                    return null;
                state = DIGIT3_OR_EQUAL;
                break;
            case DIGIT3_OR_EQUAL:
                if (c == '=') {
                    state = EQUAL;
                    continue;
                } else {
                    if (c > 255 || (value = fromDigit[c]) < 0)
                        return null;
                    state = DIGIT4_OR_EQUAL;
                }
                break;
            case EQUAL:
                if (c == '=') {
                    state = END_OR_SPACE;
                    continue;
                } else {
                    return null;
                }
                /*break;*/
            case DIGIT4_OR_EQUAL:
                if (c == '=') {
                    state = END_OR_SPACE;
                    continue;
                } else {
                    if (c > 255 || (value = fromDigit[c]) < 0)
                        return null;
                    state = SPACE_OR_DIGIT1;
                }
                break;
            case END_OR_SPACE:
                if (Character.isWhitespace(c)) {
                    // Same state.
                    continue;
                } else {
                    return null;
                }
                /*break;*/
            default:
                throw new RuntimeException("unknown state " + state);
            }

            bits <<= 6; 
            bits |= value; 
            bitCount += 6;

            if (bitCount >= 8) {
                bitCount -= 8;
                b[j++] = (byte) ((bits >> bitCount) & 0xFF);
            }
        }

        switch (state) {
        case SPACE_OR_DIGIT1:
        case DIGIT1:
        case END_OR_SPACE:
            break;
        default:
            return null;
        }

        if (j != b.length) {
            byte[] b2 = new byte[j];
            System.arraycopy(b, 0, b2, 0, j);
            b = b2;
        }

        return b;
    }

    /*TEST_DECODE_WORDS
    public static void main(String[] args) {
        String[] encoded = {
            "",

            "From: =?US-ASCII?Q?Keith_Moore?= <moore@cs.utk.edu>",

            "To: =?ISO-8859-1?Q?Keld_J=F8rn_Simonsen?= <keld@dkuug.dk>",

            "CC: =?ISO-8859-1?Q?Andr=E9?= Pirard <PIRARD@vm1.ulg.ac.be>",

            "Subject: =?ISO-8859-1?B?SWYgeW91IGNhbiByZWFkIHRoaXMgeW8=?=" +
            "=?ISO-8859-2?B?dSB1bmRlcnN0YW5kIHRoZSBleGFtcGxlLg==?=",

            "From: =?ISO-8859-1?Q?Olle_J=E4rnefors?= <ojarnef@admin.kth.se>",

            "To: ietf-822@dimacs.rutgers.edu, ojarnef@admin.kth.se",

            "Subject: Time for ISO 10646?",

            "To: Dave Crocker <dcrocker@mordor.stanford.edu>",

            "Cc: ietf-822@dimacs.rutgers.edu, paf@comsol.se",

            "From: =?ISO-8859-1?Q?Patrik_F=E4ltstr=F6m?= <paf@nada.kth.se>",

            "Subject: Re: RFC-HDR care and feeding",

            "From: Nathaniel Borenstein <nsb@thumper.bellcore.com>" +
            " (=?iso-8859-8?b?7eXs+SDv4SDp7Oj08A==?=)",

            "To: Greg Vaudreuil <gvaudre@NRI.Reston.VA.US>, Ned Freed" +
            " <ned@innosoft.com>, Keith Moore <moore@cs.utk.edu>",

            "Subject: Test of new header generator",

            "MIME-Version: 1.0",

            "Content-type: text/plain; charset=ISO-8859-1",

            "Content-disposition: form-data;" +
            " name==?iso-8859-1?q?La_belle_=E9quipe?=;" +
            " filename==?utf-8?b?bGEgYmVsbGUgw6lxdWlwZS5qcGVn?="
        };

        for (int i = 0; i < encoded.length; ++i) {
            System.out.println("{" + encoded[i] + "}");

            String decoded;
            try {
                decoded = decodeWords(encoded[i]);
                System.out.println("= {" + decoded + "}\n");
            } catch (IOException e) {
                System.err.println("*** error: " + e.getMessage());
            }
        }
    }
    TEST_DECODE_WORDS*/
}
