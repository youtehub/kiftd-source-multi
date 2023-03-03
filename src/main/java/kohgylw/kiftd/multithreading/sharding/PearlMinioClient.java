package kohgylw.kiftd.multithreading.sharding;

import com.google.common.collect.Multimap;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import io.minio.messages.Part;
import okhttp3.Response;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Created by TD on 2023/03/03
 * 扩展 MinioClient <很多protected 修饰符的分片方法，MinioClient实例对象无法使用，只能自定义类继承使用>
 * minio 大文件分片上传思路：
 * 1. 前端访问文件服务，请求上传文件，后端返回签名数据及uploadId
 * 2. 前端分片文件，携带签名数据及uploadId并发上传分片数据
 * 3. 分片上传完成后，访问合并文件接口，后台负责合并文件。
 */
public class PearlMinioClient extends S3Base {


    protected PearlMinioClient(S3Base client) {
        super(client);
    }

    /**
     * 创建分片上传请求
     *
     * @param bucketName       存储桶
     * @param region           区域
     * @param objectName       对象名
     * @param headers          消息头
     * @param extraQueryParams 额外查询参数
     */
    @Override
    public CreateMultipartUploadResponse createMultipartUpload(String bucketName, String region, String objectName, Multimap<String, String> headers, Multimap<String, String> extraQueryParams) throws NoSuchAlgorithmException, InsufficientDataException, IOException, InvalidKeyException, ServerException, XmlParserException, ErrorResponseException, InternalException, InvalidResponseException {
        return super.createMultipartUpload(bucketName, region, objectName, headers, extraQueryParams);
    }

    /**
     * 完成分片上传，执行合并文件
     *
     * @param bucketName       存储桶
     * @param region           区域
     * @param objectName       对象名
     * @param uploadId         上传ID
     * @param parts            分片
     * @param extraHeaders     额外消息头
     * @param extraQueryParams 额外查询参数
     */
    @Override
    public ObjectWriteResponse completeMultipartUpload(String bucketName, String region, String objectName, String uploadId, Part[] parts, Multimap<String, String> extraHeaders, Multimap<String, String> extraQueryParams) throws NoSuchAlgorithmException, InsufficientDataException, IOException, InvalidKeyException, ServerException, XmlParserException, ErrorResponseException, InternalException, InvalidResponseException {
        return super.completeMultipartUpload(bucketName, region, objectName, uploadId, parts, extraHeaders, extraQueryParams);
    }

    /**
     * @param bucketName       存储桶
     * @param region           区域
     * @param objectName       对象名
     * @param partSource       分片对象
     * @param partNumber       分片数
     * @param uploadId         上传ID
     * @param extraHeaders     额外消息头
     * @param extraQueryParams 额外查询参数
     */
    public UploadPartResponse uploadPart(String bucketName, String region, String objectName, PartSource partSource, int partNumber, String uploadId, Multimap<String, String> extraHeaders, Multimap<String, String> extraQueryParams) throws NoSuchAlgorithmException, InsufficientDataException, IOException, InvalidKeyException, ServerException, XmlParserException, ErrorResponseException, InternalException, InvalidResponseException {
        Response response = this.execute(Method.PUT, bucketName, objectName, this.getRegion(bucketName, region), this.httpHeaders(extraHeaders), this.merge(extraQueryParams, this.newMultimap("partNumber", Integer.toString(partNumber), "uploadId", uploadId)), partSource, 0);
        Throwable var10 = null;
        UploadPartResponse var11;
        try {
            var11 = new UploadPartResponse(response.headers(), bucketName, region, objectName, uploadId, partNumber, response.header("ETag").replaceAll("\"", ""));
        } catch (Throwable var20) {
            var10 = var20;
            throw var20;
        } finally {
            if (response != null) {
                if (var10 != null) {
                    try {
                        response.close();
                    } catch (Throwable var19) {
                        var10.addSuppressed(var19);
                    }
                } else {
                    response.close();
                }
            }

        }

        return var11;
    }

    /**
     * 查询分片数据
     *
     * @param bucketName       存储桶
     * @param region           区域
     * @param objectName       对象名
     * @param uploadId         上传ID
     * @param extraHeaders     额外消息头
     * @param extraQueryParams 额外查询参数
     */
    public ListPartsResponse listMultipart(String bucketName, String region, String objectName, Integer maxParts, Integer partNumberMarker, String uploadId, Multimap<String, String> extraHeaders, Multimap<String, String> extraQueryParams) throws NoSuchAlgorithmException, InsufficientDataException, IOException, InvalidKeyException, ServerException, XmlParserException, ErrorResponseException, InternalException, InvalidResponseException {
        return super.listParts(bucketName, region, objectName, maxParts, partNumberMarker, uploadId, extraHeaders, extraQueryParams);
    }
}
