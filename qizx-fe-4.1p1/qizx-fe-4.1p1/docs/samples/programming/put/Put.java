/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package put;

import com.qizx.api.Collection;
import com.qizx.api.Library;
import com.qizx.api.LibraryManager;
import com.qizx.api.LibraryManagerFactory;
import com.qizx.api.LibraryMember;
import com.qizx.api.QizxException;
import com.qizx.xdm.DocumentParser;
import com.qizx.xlib.XMLLibrarySession;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

public class Put
{
    public static void main(String[] args)
        throws IOException, QizxException, SAXException
    {
        FileFilter filter = null;
        int l;

        for (l = 0; l < args.length; ++l) {
            String arg = args[l];

            if ("-f".equals(arg)) {
                if (l + 1 >= args.length) {
                    usage(null);
                    /*NOTREACHED*/
                }

                filter = new SimpleFileFilter(args[++l]);
            }
            else {
                if (arg.startsWith("-")) {
                    usage(null);
                }

                break;
            }
        }

        if (l + 4 > args.length) {
            usage(null);
        }
        File storageDir = new File(args[l]);
        String libName = args[l + 1];
        int last = args.length - 1;
        String dstPath = args[last];

        LibraryManager libManager = getLibraryManager(storageDir);
        Library lib = getLibrary(libManager, libName);

        LibraryMember dst = lib.getMember(dstPath);
        boolean dstIsCollection = (dst != null && dst.isCollection());

        if (args.length > l + 4 && !dstIsCollection) {
            shutdown(lib, libManager);
            usage("'" + dstPath + "', does not exist or is a document");
        }

        try {
            for (int i = l + 2; i < last; ++i) {
                File srcFile = new File(args[i]);

                String dstPath2 = dstPath;
                if (dstIsCollection) {
                    dstPath2 = joinPath(dstPath, srcFile.getName());
                }
                put(lib, srcFile, filter, dstPath2);
            }

            verbose("Committing changes...");
            lib.commit();
        }
        catch(Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        finally {
            shutdown(lib, libManager);
        }
    }

    private static void usage(String message)
    {
        if (message != null)
            System.err.println("*** Error: " + message);
        System.err.println("usage: java Put ?-f filter? libraries_storage_dir library_name"
                           + " source+ destination\n"
                           + "  -f filter Included and/or excluded file extensions.\n"
                           + "      Syntax: incl_ext1,...,incl_extN\n"
                           + "              | -excl_ext1,...,excl_extM.\n"
                           + "  libraries_storage_dir Directory containing libraries.\n"
                           + "      If this directory does not exist, it is created.\n"
                           + "  library_name Name of library containing destination documents\n"
                           + "      and collections.\n"
                           + "      If this library does not exist, it is created.\n"
                           + "  source Source XML file or directory containing XML files.\n"
                           + "  destination Absolute path of destination document or\n"
                           + "      destination collection.\n"
                           + "      If multiple sources are specified, destination must be\n"
                           + "      an existing collection.");
        System.exit(1);
    }

    private static LibraryManager getLibraryManager(File storageDir)
        throws IOException, QizxException
    {
        LibraryManagerFactory factory = LibraryManagerFactory.getInstance();
        if (storageDir.exists()) {
            return factory.openLibraryGroup(storageDir);
        }
        else {
            if (!storageDir.mkdirs()) {
                throw new IOException("cannot create directory '" + storageDir
                                      + "'");
            }

            verbose("Creating library group in '" + storageDir + "'...");
            return factory.createLibraryGroup(storageDir);
        }
    }

    private static Library getLibrary(LibraryManager libManager, String libName)
        throws QizxException
    {
        Library lib = libManager.openLibrary(libName);
        if (lib == null) {
            verbose("Creating library '" + libName + "'...");
            libManager.createLibrary(libName, null/*inside root*/);
            lib = libManager.openLibrary(libName);
        }
        return lib;
    }

    private static void put(Library lib, File srcFile, FileFilter filter,
                            String dstPath)
        throws IOException, QizxException, SAXException
    {
        if (srcFile.isDirectory()) {
            Collection collection = lib.getCollection(dstPath);
            if (collection == null) {
                verbose("Creating collection '" + dstPath + "'...");
                collection = lib.createCollection(dstPath);
            }

            File[] files = srcFile.listFiles(filter);
            if (files == null) {
                throw new IOException("cannot list directory '" + srcFile
                                      + "'");
            }

            for (int i = 0; i < files.length; ++i) {
                File file = files[i];
                put(lib, file, filter, joinPath(dstPath, file.getName()));
            }
        }
        else {
            verbose("Importing '" + srcFile + "' as document '" + dstPath
                    + "'...");
            XMLReader parser = new DocumentParser().newParser();
            parser.setFeature("http://apache.org/xml/features/xinclude", true);
            lib.importDocument(dstPath,
                          new InputSource(srcFile.getCanonicalPath()), parser);
        }
    }

    private static void shutdown(Library lib, LibraryManager libManager)
        throws QizxException
    {
        if (lib.isModified()) {
            lib.rollback();
        }
        lib.close();
        libManager.closeAllLibraries(10000 /*ms*/);
    }

    private static void verbose(String message)
    {
        System.out.println(message);
    }


    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static final class SimpleFileFilter
        implements FileFilter
    {
        private String[] suffixes;
        private boolean negate;

        public SimpleFileFilter(String pattern)
        {
            negate = false;
            if (pattern.startsWith("-")) {
                negate = true;
                pattern = pattern.substring(1);
            }

            String[] extensions = pattern.split(",");

            suffixes = new String[extensions.length];
            int j = 0;

            for (int i = 0; i < extensions.length; ++i) {
                String ext = extensions[i];
                if (ext.length() > 0)
                    suffixes[j++] = "." + ext;
            }

            if (j != suffixes.length) {
                String[] suffixes2 = new String[j];
                System.arraycopy(suffixes, 0, suffixes2, 0, j);
                suffixes = suffixes2;
            }
        }

        public boolean accept(File file)
        {
            if (file.isDirectory())
                return true;

            String name = file.getName();

            boolean match = false;
            for (int i = 0; i < suffixes.length; ++i) {
                if (name.endsWith(suffixes[i])) {
                    match = true;
                    break;
                }
            }

            return negate ? !match : match;
        }
    }

    private static String parentPath(String path)
    {
        if (path == null || path.length() == 0 || path.charAt(0) != '/')
            throw new IllegalArgumentException("'" + path
                                               + "', not an absolute path");

        if ("/".equals(path))
            return null;

        int pos = path.lastIndexOf('/');
        if (pos == 0) {
            return "/";
        }
        else {
            // Cannot be < 0 because path is absolute.
            return path.substring(0, pos);
        }
    }

    private static String joinPath(String path1, String path2)
    {
        StringBuffer buffer = new StringBuffer(path1);
        if (!path2.startsWith("/") && !path1.endsWith("/"))
            buffer.append('/');
        buffer.append(path2);

        return buffer.toString();
    }
}
