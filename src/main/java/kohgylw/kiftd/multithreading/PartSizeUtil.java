package kohgylw.kiftd.multithreading;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import kohgylw.kiftd.multithreading.minio.MinioResp;
import kohgylw.kiftd.multithreading.minio.MinioUtil;
import kohgylw.kiftd.multithreading.minio.ObjectItem;
import kohgylw.kiftd.server.util.ConfigureReader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @description： minio工具类
 * @version：3.0
 */
public class PartSizeUtil {

    private static final Integer maxThread = 500;


    public static synchronized void clearCache(MinioResp minioResp, String tempFile) {
        MinioUtil minioUtil = MinioUtil.init();
        if (ObjUtil.isNotEmpty(minioResp)) {
            List<String> allFilePath = getAllFilePath(minioResp.getFileName(), null);
            allFilePath.forEach(FileUtil::del);

            String bucket = minioResp.getBucket();
            List<ObjectItem> objectItems = minioUtil.listBucket(bucket);
            minioUtil.deleteObject(bucket, objectItems.get(0).getObjectName());
            minioUtil.removeBucket(minioResp.getBucket());
        }
        if (StrUtil.equals("tempdir", tempFile)) {
            ConfigureReader instance = ConfigureReader.instance();
            String tempDir = instance.serverp.getProperty("local.temp.dir");
            List<String> allFilePath = getAllFilePath(tempFile, null);
            allFilePath.forEach(FileUtil::del);
            FileUtil.del(tempDir);
            FileUtil.mkdir(tempDir);

            List<String> existBuckets = minioUtil.listBuckets();
            existBuckets.forEach(bt -> {
                List<ObjectItem> objectItems = minioUtil.listBucket(bt);
                objectItems.forEach(obj -> minioUtil.deleteObject(bt, obj.getObjectName()));
                minioUtil.removeBucket(bt);
            });
        }
    }

    /**
     * 1.得到该路径下的所有文件路径
     */
    public static List<String> getAllFilePath(String path, List<String> allFilePath) {
        if (allFilePath == null) {
            allFilePath = new ArrayList<String>();
        }
        File file = new File(path);
        if (!file.isDirectory()) {
            allFilePath.add(path);
            return allFilePath;
        } else if (file.isDirectory()) {
            String[] fileList = file.list();
            for (String fileName : fileList) {
                String subFileName = path + File.separator + fileName;
                File subFile = new File(subFileName);
                if (!subFile.isDirectory()) {
                    String parent = subFile.getParent();
                    allFilePath.add(subFileName);
                } else if (subFile.isDirectory()) {
                    getAllFilePath(subFileName, allFilePath);
                }
            }
            return allFilePath;
        }
        return null;
    }

    public static String mkdir(String tempDires) {
        ConfigureReader instance = ConfigureReader.instance();
        String tempDir = instance.serverp.getProperty("local.temp.dir");
        tempDires = tempDir + "\\" + tempDires;
        FileUtil.mkdir(tempDires);
        return tempDires;
    }


    /**
     * @param fileSize 文件大小
     * @param block    分块大小，单位 M
     * @return
     */
    public static int partSize(Long fileSize, Integer block) {
        PartSizeUtil partSizeUtil = new PartSizeUtil();
        long[] partInfo = partSizeUtil.getPartInfo(fileSize, block);
        int thread = (int) partInfo[1];
        return thread < maxThread ? thread : maxThread;
    }


    /**
     * minio 默认最新拆分文件的值
     */
    public static Long default_size = 128 * 1024L;

    // allowed maximum object size is 5TiB.
    public static final long MAX_OBJECT_SIZE = 5L * 1024 * 1024 * 1024 * 1024;
    // allowed minimum part size is 5MiB in multipart upload.
    public static final int MIN_MULTIPART_SIZE = 5 * 1024 * 1024;
    // allowed minimum part size is 5GiB in multipart upload.
    public static final long MAX_PART_SIZE = 5L * 1024 * 1024 * 1024;
    public static final int MAX_MULTIPART_COUNT = 10000;

    private long[] getPartInfo(long objectSize, long partSize) {
        if (objectSize < 0) return new long[]{partSize, -1};

        if (partSize <= 0) {
            // Calculate part size by multiple of MIN_MULTIPART_SIZE.
            double dPartSize = Math.ceil((double) objectSize / MAX_MULTIPART_COUNT);
            dPartSize = Math.ceil(dPartSize / MIN_MULTIPART_SIZE) * MIN_MULTIPART_SIZE;
            partSize = (long) dPartSize;
        }

        if (partSize > objectSize) partSize = objectSize;
        long partCount = partSize > 0 ? (long) Math.ceil((double) objectSize / partSize) : 1;
        if (partCount > MAX_MULTIPART_COUNT) {
            throw new IllegalArgumentException("object size " + objectSize + " and part size " + partSize + " make more than " + MAX_MULTIPART_COUNT + "parts for upload");
        }

        return new long[]{partSize, partCount};
    }


}


