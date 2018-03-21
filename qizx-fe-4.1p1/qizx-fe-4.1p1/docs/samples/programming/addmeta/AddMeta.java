/*
 *    Qizx Free_Engine-4.1p1
 *
 *    This code is part of the Qizx application components
 *    Copyright (c) 2004-2010 Axyana Software -- All rights reserved.
 *
 *    For conditions of use, see the accompanying license files.
 */
package addmeta;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Date;
import java.util.HashMap;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import com.qizx.api.QizxException;
import com.qizx.api.LibraryMember;
import com.qizx.api.Collection;
import com.qizx.api.LibraryMemberIterator;
import com.qizx.api.Library;
import com.qizx.api.LibraryManager;
import com.qizx.api.LibraryManagerFactory;

public class AddMeta
{
    public static void main(String[] args) 
        throws IOException, QizxException {
        if (args.length != 5) {
            usage();
            /*NOTREACHED*/
        }
        File storageDir = new File(args[0]);
        String libName = args[1];
        String collectionPath = args[2];
        File infoFile = new File(args[3]);
        File licenseFile = new File(args[4]);
        
        HashMap nameToInfo = loadInfo(infoFile);
        String license = loadFile(licenseFile);

        LibraryManagerFactory factory = LibraryManagerFactory.getInstance();
        LibraryManager libManager = factory.openLibraryGroup(storageDir);
        Library lib = libManager.openLibrary(libName);

        try {
            addMeta(lib, collectionPath, nameToInfo, license);

            verbose("Committing changes...");
            lib.commit();
        } finally {
            shutdown(lib, libManager);
        }
    }

    private static void usage() {
        System.err.println(
          "usage: java AddMeta libraries_storage_dir library_name" +
          " collection_path info_file license_file\n" +
          "  libraries_storage_dir Directory containing libraries.\n" +
          "  library_name Name of library containing documents\n" +
          "      and collections to be deleted.\n" +
          "  collection_path Absolute path of collection containing\n" +
          "      documents to which meta-data are to be added.\n" +
          "      These documents are local copies of Wikipedia pages.\n" +
          "  info_file File containing info about the local copies of\n" +
          "      Wikipedia pages.\n" +
          "  license_file File containing the copyright of Wikipedia pages.");

        System.exit(1);
    }

    private static void addMeta(Library lib, String collectionPath,
                                HashMap nameToInfo, String license) 
       throws QizxException {
        Collection collection = lib.getCollection(collectionPath);
        if (collection == null) {
            error("'" + collectionPath + "' is not a collection");
            return;
        }

        LibraryMemberIterator iter = collection.getChildren();
        while (iter.moveToNextMember()) {
            LibraryMember m = iter.getCurrentMember();

            if (m.isDocument()) {
                String name = trimExtension(m.getName());

                Info info = (Info) nameToInfo.get(name);
                if (info == null) {
                    warning("No meta-data about '" + m.getPath() + "'...");
                } else {
                    verbose("Adding meta-data to '" + m.getPath() + "'...");
                    m.setProperty("copyDate", info.copyDate);
                    m.setProperty("copiedURL", info.copiedURL);
                    m.setProperty("license", license);
                }
            }
        }
    }

    private static void shutdown(Library lib, LibraryManager libManager) 
        throws QizxException {
        if (lib.isModified()) {
            lib.rollback();
        }
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
    // Info about local copies of Wikipedia pages
    // -----------------------------------------------------------------------

    private static SimpleDateFormat DATE_FORMAT = 
        new SimpleDateFormat("HH:mm, d MMMM yyyy", Locale.US);
    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
        DATE_FORMAT.setLenient(false);
    }

    private static final class Info {
        public final Date copyDate;
        public final URL copiedURL;

        public Info(Date copyDate, URL copiedURL) {
            this.copyDate = copyDate;
            this.copiedURL = copiedURL;
        }
    }

    private static HashMap loadInfo(File file) 
        throws IOException {
        LineNumberReader lines = new LineNumberReader(
            new InputStreamReader(new FileInputStream(file), "UTF-8"));
        HashMap nameToInfo = new HashMap();
        try {
            String line;
            while ((line = lines.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }

                int pos = line.indexOf(';');
                if (pos <= 0 || pos == line.length()-1) {
                    throw new IOException("syntax error in file '" + file + 
                                         "' at line " + lines.getLineNumber());
                }

                String dateSpec = line.substring(0, pos);
                String urlSpec = line.substring(pos+1);

                String documentName = null;
                Date date = null;
                URL url = null;

                try {
                    date = DATE_FORMAT.parse(dateSpec);
                } catch (ParseException ignored) {}

                try {
                    URI uri = new URI(urlSpec);

                    String path = uri.getPath();
                    documentName = baseName(path);
                    if (documentName.length() == 0)
                        documentName = null;

                    url = uri.toURL();
                } catch (Exception ignored) {}

                if (documentName == null || date == null || url == null) {
                    throw new IOException("syntax error in file '" + file + 
                                         "' at line " + lines.getLineNumber());
                }

                nameToInfo.put(documentName, new Info(date, url));
            }
        } finally {
            lines.close();
        }

        return nameToInfo;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String trimExtension(String path) {
        int slash = path.lastIndexOf('/');
        if (slash < 0) 
            slash = 0;
        else 
            ++slash;

        int dot = path.lastIndexOf('.');
        if (dot <= slash)
            // '.profile' has no extension!
            return path;
        else
            return path.substring(0, dot);
    }

    private static String baseName(String path) {
        int slash = path.lastIndexOf('/');
        if (slash < 0)
            return path;
        else if (slash == path.length()-1)
            return "";
        else
            return path.substring(slash+1);
    }

    private static String loadFile(File file) 
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
