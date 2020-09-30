package io.nebula.platform.khala.plugin.resolve;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author : panxinghai
 * date : 2019-06-25 18:06
 */
public class DuplicateHelper {
    public static HashMap<Long, ArrayList<String>> checkDuplicate(File ap_File) {
        ZipFile zipFile = null;
        HashMap<Long, String> crcMap = new HashMap<>();
        HashMap<Long, ArrayList<String>> map = new HashMap<>();
        int count = 0;
        int repeatCount = 0;
        long totalDuplicateSize = 0;
        try {
            // open a zip file for reading
            zipFile = new ZipFile(ap_File);
            // get an enumeration of the ZIP file entries
            Enumeration<? extends ZipEntry> e = zipFile.entries();
            while (e.hasMoreElements()) {
                ZipEntry entry = e.nextElement();
                // get the name of the entry
                String entryName = entry.getName();

                // get the CRC-32 checksum of the uncompressed entry data, or -1 if not known
                long crc = entry.getCrc();

//                System.out.println(entryName + " with CRC-32: " + crc);
                count++;
                String value = crcMap.get(crc);
                if (value == null) {
                    crcMap.put(crc, entryName);
                }else {
                    repeatCount++;
                    totalDuplicateSize += entry.getSize();
                    ArrayList<String> list = map.get(crc);
                    if (list == null) {
                        list = new ArrayList<>();
                        map.put(crc, list);
                        list.add(value);
                    }
                    list.add(entryName);
                }
            }
            System.out.println("resource file count:" + count + "; found repeatCount:" + repeatCount + "; duplicate resource total size:" + totalDuplicateSize);
            for (Map.Entry<Long, ArrayList<String>> entry : map.entrySet()) {
//                System.out.println("crc: " + entry.getKey() + ":" + Arrays.toString(new List[]{Collections.singletonList(entry.getValue())}));
            }
            return map;
        } catch (IOException ioe) {
            System.out.println("Error opening zip file" + ioe);
        } finally {
            try {
                if (zipFile != null) {
                    zipFile.close();
                }
            } catch (IOException ioe) {
                System.out.println("Error while closing zip file" + ioe);
            }
        }
        return map;

    }
}
