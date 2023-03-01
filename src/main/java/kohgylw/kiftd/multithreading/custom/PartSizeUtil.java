//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package kohgylw.kiftd.multithreading.custom;

import cn.hutool.core.io.FileUtil;
import kohgylw.kiftd.server.util.ConfigureReader;

public class PartSizeUtil {
    private static final Integer maxThread = 500;
    public static Long default_size = 131072L;
    public static final long MAX_OBJECT_SIZE = 5497558138880L;
    public static final int MIN_MULTIPART_SIZE = 5242880;
    public static final long MAX_PART_SIZE = 5368709120L;
    public static final int MAX_MULTIPART_COUNT = 10000;

    public PartSizeUtil() {
    }

    public static void clearCache() {
        ConfigureReader instance = ConfigureReader.instance();
        String tempDir = instance.serverp.getProperty("local.temp.dir");
        FileUtil.del(tempDir);
        FileUtil.mkdir(tempDir);
    }

    public static int partSize(Long fileSize, Integer block) {
        PartSizeUtil partSizeUtil = new PartSizeUtil();
        long[] partInfo = partSizeUtil.getPartInfo(fileSize, (long)block);
        int thread = (int)partInfo[1];
        return thread < maxThread ? thread : maxThread;
    }

    private long[] getPartInfo(long objectSize, long partSize) {
        if (objectSize < 0L) {
            return new long[]{partSize, -1L};
        } else {
            if (partSize <= 0L) {
                double dPartSize = Math.ceil((double)objectSize / 10000.0);
                dPartSize = Math.ceil(dPartSize / 5242880.0) * 5242880.0;
                partSize = (long)dPartSize;
            }

            if (partSize > objectSize) {
                partSize = objectSize;
            }

            long partCount = partSize > 0L ? (long)Math.ceil((double)objectSize / (double)partSize) : 1L;
            if (partCount > 10000L) {
                throw new IllegalArgumentException("object size " + objectSize + " and part size " + partSize + " make more than " + 10000 + "parts for upload");
            } else {
                return new long[]{partSize, partCount};
            }
        }
    }
}
