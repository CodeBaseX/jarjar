/*
  Jar Jar Links - A utility to repackage and embed Java libraries
  Copyright (C) 2004  Tonic Systems, Inc.

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; see the file COPYING.  if not, write to
  the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA 02111-1307 USA
*/

package com.tonicsystems.jarjar;

import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.util.jar.*;

class ClassPathIterator
implements Iterator
{
    private LinkedList parts = new LinkedList();

    private static final FileFilter FILTER = new FileFilter() {
        public boolean accept(File pathname) {
            return pathname.isDirectory() || isClassFile(getName(pathname));
        }
    };

    private File parent;
    private Enumeration entries;
    private ZipFile prevZip;
    private ZipFile zip;
    private Object next;
    
    public ClassPathIterator(File parent, String classPath)
    {
        this.parent = parent;
        StringTokenizer st = new StringTokenizer(classPath, ":");
        while (st.hasMoreTokens())
            parts.add(st.nextElement());
        advance();
    }

    public boolean hasNext()
    {
        return next != null;
    }

    public void close()
    throws IOException
    {
        if (prevZip != null) {
            prevZip.close();
            prevZip = null;
        }
    }
    
    public ZipFile getZipFile()
    {
        return zip;
    }

    public InputStream getInputStream(Object obj)
    throws IOException
    {
        if (obj instanceof ZipEntry) {
            return zip.getInputStream((ZipEntry)obj);
        } else {
            return new BufferedInputStream(new FileInputStream((File)obj));
        }
    }

    public void remove()
    {
        throw new UnsupportedOperationException();
    }

    public Object next()
    {
        if (!hasNext())
            throw new NoSuchElementException();
        Object result = next;
        advance();
        return result;
    }

    private void advance()
    {
        try {
            close();
            if (entries == null) {
                prevZip = zip;
                if (parts.size() == 0) {
                    next = null;
                    return;
                }

                String part = (String)parts.removeFirst();
                File file = new File(part);
                if (!file.isAbsolute())
                    file = new File(parent, part);

                int len = part.length();
                if (len >= 4) {
                    String ext = part.substring(len - 4, len);
                    if (ext.equalsIgnoreCase(".jar")) {
                        zip = new JarFile(file);
                    } else if (ext.equalsIgnoreCase(".zip")) {
                        zip = new ZipFile(file);
                    } else {
                        zip = null;
                    }
                    if (zip != null)
                        entries = zip.entries();
                }
                if (entries == null) {
                    if (file.isDirectory()) {
                        // TODO: could lazily recurse for performance
                        entries = findClasses(file);
                    } else {
                        throw new IllegalArgumentException("Do not know how to handle " + part);
                    }
                }
            }
            boolean foundClass = false;
            while (entries.hasMoreElements()) {
                next = entries.nextElement();
                if (foundClass = isClassFile(getName(next)))
                    break;
            }
            if (!foundClass)
                next = null;
            if (!entries.hasMoreElements())
                entries = null;
        } catch (IOException e) {
            throw new WrappedIOException(e);
        }
    }

    private static Enumeration findClasses(File root)
    {
        List collect = new ArrayList();
        findClassesHelper(root, collect);
        return Collections.enumeration(collect);
    }

    private static void findClassesHelper(File dir, List collect)
    {
        File[] files = dir.listFiles(FILTER);
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                findClassesHelper(files[i], collect);
            } else {
                collect.add(files[i]);
            }
        }
    }

    private static String getName(Object obj)
    {
        return (obj instanceof ZipEntry) ? ((ZipEntry)obj).getName() : ((File)obj).getName();
    }

    private static boolean isClassFile(String name)
    {
        int len = name.length();
        return (len >= 6) && name.substring(len - 6, len).equalsIgnoreCase(".class");
    }
}
