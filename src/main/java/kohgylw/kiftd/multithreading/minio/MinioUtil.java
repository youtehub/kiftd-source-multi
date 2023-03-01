package kohgylw.kiftd.multithreading.minio;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.extra.spring.SpringUtil;
import io.minio.*;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import kohgylw.kiftd.server.util.ConfigureReader;
import kohgylw.kiftd.server.util.KiftdProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @description： minio工具类
 * @version：3.0
 */
@Slf4j
public class MinioUtil {

    private MinioClient minioClient;

    private String bucketName;

    private long partSize;

    public MinioUtil() {
    }

    public MinioUtil(String bucketName) {
        this.bucketName = bucketName;
    }

    public MinioUtil(MinioClient minioClient, long partSize) {
        this.minioClient = minioClient;
        this.partSize = partSize;
    }

    public static MinioUtil init() {
        KiftdProperties serverp = ConfigureReader.instance().serverp;
        String endpoint = serverp.getProperty("minio.endpoint");
        String accesskey = serverp.getProperty("minio.access-key");
        String endpointsecret = serverp.getProperty("minio.access-secret");
        long partSize = Long.parseLong(serverp.getProperty("minio.access-partsize"));
        MinioClient minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accesskey, endpointsecret)
                .build();
        MinioUtil minioUtil = new MinioUtil(minioClient, partSize);
        return minioUtil;
    }

    public List<String> listBuckets() {
        List<String> defaults = CollUtil.newArrayList("baidubarrel", "thunderbarrel");
        List<String> list = new ArrayList<>();
        try {
            List<Bucket> bucketList = minioClient.listBuckets();
            list = bucketList.stream()
                    .map(Bucket::name)
                    .filter(name -> !defaults.contains(name))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return list;
        }
        return list;
    }

    /**
     * 查看桶的所有文件对象
     *
     * @param bucketName 存储bucket名称
     * @return 存储bucket内文件对象信息
     */
    public List<ObjectItem> listBucket(String bucketName) {
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).build());
        List<ObjectItem> objectItems = new ArrayList<>();
        try {
            for (Result<Item> result : results) {
                Item item = result.get();
                ObjectItem objectItem = new ObjectItem();
                objectItem.setObjectName(item.objectName().replaceAll("a-zA-Z0-9_\\u4e00-\\u9fa5", "").replaceAll("/", ""));
                objectItem.setSize(item.size());
                objectItems.add(objectItem);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return objectItems;
    }

    public File getFile(String bucket, String file, File tempFile) {
        InputStream stream;
        try {
            stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(file)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        FileUtil.writeFromStream(stream, tempFile);
        return tempFile;
    }

    public void createBucket(String bucketName) {
        try {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucketName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void removeBucket(String bucketName) {
        try {
            minioClient.removeBucket(
                    RemoveBucketArgs.builder()
                            .bucket(bucketName)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 查看桶的内指定文件
     *
     * @param bucketName 存储bucket名称
     * @param object     文件名称
     * @return 存储bucket内文件对象信息
     */
    public List<ObjectItem> listBucketObjects(String bucketName, String object) {
        List<ObjectItem> objectItems = new ArrayList<>();
        try {
            InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(object)
                            .build());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return objectItems;
    }

    /**
     * 判断文件夹是否存在
     *
     * @param bucketName
     * @param objectName
     * @return
     */
    public boolean isFileExist(String bucketName, String objectName) {
        boolean exist = false;
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(objectName)
                            .recursive(true)
                            .build());
            for (Result<Item> result : results) {
                Item item = result.get();
                String fileName = getUtf8ByURLDecoder(item.objectName());
                if (fileName.contains(objectName)) {
                    exist = true;
                }
            }
        } catch (Exception e) {
            log.error("[Minio工具类]>>>> 判断文件夹是否存在，异常：", e);
            exist = false;

        }
        return exist;
    }

    /**
     * 删除文件夹及文件
     *
     * @param bucketName bucket名称
     * @param objectName 文件或文件夹名称
     * @since tarzan LIU
     */
    public Boolean deleteObject(String bucketName, String objectName) {
        Boolean flag = true;
        if (objectName.contains(".") || objectName.endsWith("/")) {
            try {
                minioClient.removeObject(
                        RemoveObjectArgs
                                .builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .build());
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
        return flag;
    }

    /**
     * 上传文件
     *
     * @param filePath 要被上传文件的路径，不能为空（eg: 201403/1910585304069fdj.png）
     * @param ins      要被上传文件的输入流，不能为空
     */
    public FutureResponse uploadFile(String bucketName, String filePath, InputStream ins, Long fileSize) {
        long[] partInfo = MinioUtil.getPartInfo(fileSize, partSize);
        ObjectWriteResponse writeResponse = null;
        try {
            writeResponse = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filePath)
                            .stream(ins, fileSize, partSize)
                            .build());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new FutureResponse(partInfo, writeResponse);
    }


    /**
     * 将URLDecoder编码转成UTF8
     *
     * @param str
     * @return
     * @throws UnsupportedEncodingException
     */
    public String getUtf8ByURLDecoder(String str) {
        String url = str.replaceAll("%(?![0-9a-fA-F]{2})", "%25");
        String urlFile = null;
        try {
            urlFile = URLDecoder.decode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return urlFile;
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


    public static String getAppProps(String key) {
        Environment environment = SpringUtil.getApplicationContext().getBean(Environment.class);
        return environment.getProperty(key).trim();
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

    public static long[] getPartInfo(long objectSize, long partSize) {
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
            throw new IllegalArgumentException(
                    "object size "
                            + objectSize
                            + " and part size "
                            + partSize
                            + " make more than "
                            + MAX_MULTIPART_COUNT
                            + "parts for upload");
        }

        return new long[]{partSize, partCount};
    }


}


