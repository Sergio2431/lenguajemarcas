/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package edit;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import com.qizx.api.QizxException;
import com.qizx.api.Message;
import com.qizx.api.CompilationException;
import com.qizx.api.QName;
import com.qizx.api.Item;
import com.qizx.api.ItemSequence;
import com.qizx.api.Node;
import com.qizx.api.Expression;
import com.qizx.api.LibraryMember;
import com.qizx.api.Library;
import com.qizx.api.LibraryManager;
import com.qizx.api.LibraryManagerFactory;
import com.qizx.api.util.XMLSerializer;

/**
 *  Generic launcher for running XQuery Update scripts on an XML Library.
 *
 */
public class XUpdate
{
    public static void main(String[] args) 
        throws IOException, QizxException
    {
        int from = 0;
        int limit = Integer.MAX_VALUE;
        String queryRootPath = null;
        ArrayList<String> varNameList = new ArrayList<String>();
        ArrayList<String> varValueList = new ArrayList<String>();
        int l = 0;
        for (; l < args.length; ++l) {
            String arg = args[l];

            if ("-r".equals(arg)) {
                if (l + 1 >= args.length) {
                    usage(null);
                }

                arg = args[++l];
                queryRootPath = arg;
                if (!queryRootPath.startsWith("/")) {
                    usage("'" + arg + "', invalid value for option '-r'");
                }
            }
            else if ("-r".equals(arg)) {
                if (l+1 >= args.length) {
                    usage(null);
                }

                arg = args[++l];
                queryRootPath = arg;
                if (!queryRootPath.startsWith("/")) {
                    usage("'" + arg + "', invalid value for option '-r'");
                }
            }
            else if ("-v".equals(arg)) {
                if (l + 2 >= args.length) {
                    usage(null);
                }

                varNameList.add(args[++l]);
                varValueList.add(args[++l]);
            }
            else {
                if (arg.startsWith("-")) {
                    usage(null);
                }

                break;
            }
        }

        if (l+3 > args.length) {
            usage(null);
        }
        File storageDir = new File(args[l]);
        String libName = args[l+1];

        LibraryManagerFactory factory = LibraryManagerFactory.getInstance();
        LibraryManager libManager = factory.openLibraryGroup(storageDir);
        Library lib = libManager.openLibrary(libName);

        QName[] varNames = null;
        String[] varValues = null;
        if (varNameList.size() > 0) {
            int count = varNameList.size();
            varNames = new QName[count];
            varValues = new String[count];

            for (int i = 0; i < count; ++i) {
                String qName = varNameList.get(i);
                varNames[i] = parseQName(lib, qName);
                if (varNames[i] == null) {
                    shutdown(lib, libManager);
                    usage("'" + qName + "', invalid value for option '-v'");
                }

                varValues[i] = varValueList.get(i);
            }
        }

        LibraryMember queryRoot = null;
        if (queryRootPath != null) {
            queryRoot = lib.getMember(queryRootPath);
            if (queryRoot == null) {
                shutdown(lib, libManager);
                usage("'" + queryRootPath + 
                      "', invalid value for option '-r'");
            }
        }

        boolean multiFile = (l+3 < args.length);

        try {
            for (int i = l + 2; i < args.length; ++i) {
                File scriptFile = new File(args[i]);
                if (multiFile)
                    verbose("Evaluating '" + scriptFile + "'...");

                String script = loadScript(scriptFile);
                verbose("---\n" + script + "\n---");

                Expression expr = compileExpression(lib, script, queryRoot, 
                                                    varNames, varValues);

                evaluateExpression(expr, lib);
            }
        }
        finally {
            shutdown(lib, libManager);
        }
    }

    private static void usage(String message) {
        if (message != null)
            System.err.println("*** Error: " + message);
        System.err.println(
          "usage: java XUpdate ?options? libraries_storage_dir library_name" +
          " query+\n" +
          "  libraries_storage_dir Directory containing libraries.\n" +
          "  library_name Name of library being queried.\n" +
          "  query+ File containing an XQuery script.\n" +
          "      Encoding of file containing XQuery script must be UTF-8.\n" +
          "Options are:\n" +
          "-r path Absolute path of the implicit collection.\n" +
          "-v var_name var_value Binds query variable having specified\n" +
          "    name to specified string value.\n" +
          "    The syntax used for qualified names is:\n" +
          "    var_name = local_part\n" +
          "             | '{' namespace_URI '}' local_part\n" +
          "             | 'xml:' local_part");
        System.exit(1);
    }

    private static QName parseQName(Library lib, String qName) {
        int length = qName.length();
        if (length == 0)
            return null;

        int pos;
        if (qName.charAt(0) == '{' && (pos = qName.lastIndexOf('}')) > 0) {
            if (pos+1 == length)
                return null;

            String uri = qName.substring(1, pos);
            String localPart = qName.substring(pos+1);

            if (uri.length() == 0) {
                return lib.getQName(localPart);
            } else {
                return lib.getQName(localPart, uri);
            }
        } else {
            if (qName.startsWith("xml:")) {
                String localPart = qName.substring(4);
                return lib.getQName(localPart, 
                                    "http://www.w3.org/XML/1998/namespace");
            } else {
                return lib.getQName(qName);
            }
        }
    }

    private static Expression compileExpression(Library lib, 
                                                String script,
                                                LibraryMember queryRoot,
                                                QName[] varNames,
                                                String[] varValues) 
        throws IOException, QizxException {
        Expression expr;
        try {
            expr = lib.compileExpression(script);
        } catch (CompilationException e) {
            Message[] messages = e.getMessages();
            for (int i = 0; i < messages.length; ++i) {
                error(messages[i].toString());
            }

            throw e;
        }

        if (queryRoot != null)
            expr.bindImplicitCollection(queryRoot);

        if (varNames != null) {
            for (int i = 0; i < varNames.length; ++i) {
                expr.bindVariable(varNames[i], varValues[i], /*type*/ null);
            }
        }

        return expr;
    }

    private static void evaluateExpression(Expression expr, Library library)
        throws QizxException
    {
        /*ItemSequence results =*/ expr.evaluate();
        // we do not care about results (none in principle)
        // But we need to call commit() on the XML Library
        library.commit();
    }

    private static void showResult(XMLSerializer serializer,
                                   Item result) 
        throws QizxException {
        if (!result.isNode()) {
            System.out.println(result.getString());
            return;
        }
        Node node = result.getNode();

        serializer.reset();
        String xmlForm = serializer.serializeToString(node);
        System.out.println(xmlForm);
    }

    private static void shutdown(Library lib, LibraryManager libManager) 
        throws QizxException {
        lib.close();
        libManager.closeAllLibraries(10000 /*ms*/);
    }

    private static void error(String message) {
        System.err.println("Error: " + message);
    }

    private static void verbose(String message) {
        System.out.println(message);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String loadScript(File file) 
        throws IOException {
        InputStreamReader in =
            new InputStreamReader(new FileInputStream(file), "UTF-8");

        StringBuffer buffer = new StringBuffer();
        char[] chars = new char[8192];
        int count;
      
        try {
            while ((count = in.read(chars, 0, chars.length)) != -1) {
                if (count > 0)
                    buffer.append(chars, 0, count);
            }
        } finally {
            in.close();
        }

        return buffer.toString();
    }
}
