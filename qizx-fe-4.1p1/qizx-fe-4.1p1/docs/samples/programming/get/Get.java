/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package get;

import com.qizx.api.*;
import com.qizx.api.util.GlobFilter;
import com.qizx.api.util.XMLSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.PatternSyntaxException;

public class Get
{
    public static void main(String[] args) 
        throws IOException, QizxException
    {
        if (args.length < 4) {
            usage(null);
            /*NOTREACHED*/
        }
        File storageDir = new File(args[0]);
        String libName = args[1];
        int last = args.length-1;
        File dstFile = new File(args[last]);

        if ((args.length > 4 || wildcardsUsed(args, 2, last)) &&
            !dstFile.isDirectory()) {
            usage("'" + dstFile + "', does not exist or is a file");
        }

        LibraryManagerFactory factory = LibraryManagerFactory.getInstance();
        LibraryManager libManager = factory.openLibraryGroup(storageDir);
        Library lib = libManager.openLibrary(libName);

        try {
            for (int i = 2; i < last; ++i) {
                get(lib, args[i], dstFile);
            }
        } finally {
            shutdown(lib, libManager);
        }
    }

    private static void usage(String message) {
        if (message != null)
            System.err.println("*** Error: " + message);
        System.err.println(
          "usage: java Get libraries_storage_dir library_name" +
          " source+ destination\n" +
          "  libraries_storage_dir Directory containing libraries.\n" +
          "      If this directory does not exist, it is created.\n" +
          "  library_name Name of library containing destination documents\n" +
          "      and collections.\n" +
          "      If this library does not exist, it is created.\n" +
          "  source Absolute path of a source document or a source\n" +
          "      collection.\n" + 
          "      Collections are recursively copied.\n" + 
          "      The basename of a path may contain Unix-style (glob)\n" +
          "      '?' and '*' wildcard characters.\n" +
          "  destination destination file or destination directory.\n" +
          "      If multiple sources are specified or if wildcards,\n" +
          "      are used destination must be an existing directory.");
        System.exit(1);
    }

    private static boolean wildcardsUsed(String[] args, int first, int end) {
        for (int i = 2; i < end; ++i) {
            if (hasWildcards(args[i]))
                return true;
        }
        return false;
    }

    private static final char[] WILCARD_CHARS = { '*', '?', '[' };

    private static boolean hasWildcards(String path) {
        for (int j = 0; j < WILCARD_CHARS.length; ++j) {
            if (path.indexOf(WILCARD_CHARS[j]) >= 0)
                return true;
        }
        return false;
    }

    private static void get(Library lib, String path, File dstFile) 
        throws IOException, QizxException {
        // Remove trailing '/' if any.
        if (path.length() > 1 && path.endsWith("/"))
            path = path.substring(0, path.length()-1);

        String baseName = baseName(path);
        if (hasWildcards(baseName)) {
            GlobFilter filter;
            try {
                filter = new GlobFilter(baseName);
            } catch (PatternSyntaxException e) {
                error("'" + baseName + "' is not a valid glob pattern");
                return;
            }

            String parentPath = parent(path);
            Collection parent = lib.getCollection(parentPath);
            if (parent == null) {
                error("'" + parentPath + "' does not exist or is a document");
                return;
            }

            boolean match = false;

            LibraryMemberIterator iter = parent.getChildren(filter);
            while (iter.moveToNextMember()) {
                LibraryMember libMember = iter.getCurrentMember();
                match = true;

                get(libMember, dstFile);
            }

            if (!match) {
                warning("'" + path + "' does not match any library member");
            }
        }
        else {
            LibraryMember libMember = lib.getMember(path);
            if (libMember == null) {
                error("dont't find '" + path + "'");
                return;
            }

            get(libMember, dstFile);
        }
    }

    private static void get(LibraryMember libMember, File dstFile)
        throws IOException, QizxException
    {
        File dstFile2;
        if (dstFile.isDirectory()) {
            String baseName = libMember.getName();
            if ("/".equals(baseName))
                baseName = "root";

            dstFile2 = new File(dstFile, baseName);
        }
        else {
            dstFile2 = dstFile;
        }

        if (libMember.isCollection()) {
            getCollection((Collection) libMember, dstFile2);
        }
        else {
            getDocument((Document) libMember, dstFile2);
        }
    }

    private static void getDocument(Document doc, File dstFile) 
        throws IOException, QizxException {
        verbose("Copying document '" + doc.getPath() + 
                "' to file '" + dstFile + "'...");

        FileOutputStream out = new FileOutputStream(dstFile);
        try {
            doc.export(new XMLSerializer(out, "UTF-8"));
        } finally {
            out.close();
        }
    }

    private static void getCollection(Collection col, File dstFile) 
        throws IOException, QizxException {
        verbose("Copying collection '" + col.getPath() + 
                "' to directory '" + dstFile + "'...");

        if (!dstFile.isDirectory()) {
            verbose("Creating directory '" + dstFile + "'...");

            if (!dstFile.mkdirs()) {
                throw new IOException("Cannot create directory '" + 
                                      dstFile + "'");
            }
        }

        LibraryMemberIterator iter = col.getChildren();
        while (iter.moveToNextMember()) {
            LibraryMember libMember = iter.getCurrentMember();

            File dstFile2 = new File(dstFile, libMember.getName());
            
            if (libMember.isCollection()) {
                getCollection((Collection) libMember, dstFile2);
            } else {
                getDocument((Document) libMember, dstFile2);
            }
        }
    }

    private static void shutdown(Library lib, LibraryManager libManager) 
        throws QizxException {
        lib.close();
        libManager.closeAllLibraries(10000 /*ms*/);
    }

    private static void error(String message) {
        System.err.println("Error: " + message);
    }

    private static void warning(String message) {
        System.err.println("Warning: " + message);
    }

    private static void verbose(String message) {
        System.out.println(message);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String baseName(String path) {
        int slash = path.lastIndexOf('/');
        if (slash < 0)
            return path;
        else if (slash == path.length()-1)
            return "";
        else
            return path.substring(slash+1);
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        if (slash < 0)
            return null;
        else if (slash == 0)
            return (path.length() == 1)? null : "/";
        else
            return path.substring(0, slash);
    }
}
          
