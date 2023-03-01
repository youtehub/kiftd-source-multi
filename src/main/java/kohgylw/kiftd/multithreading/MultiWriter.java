package kohgylw.kiftd.multithreading;

import io.minio.MinioClient;
import kohgylw.kiftd.multithreading.minio.MinioResp;
import kohgylw.kiftd.multithreading.minio.MinioUtil;
import kohgylw.kiftd.multithreading.minio.ObjectItem;
import kohgylw.kiftd.server.util.ConfigureReader;
import kohgylw.kiftd.server.util.EncodeUtil;
import kohgylw.kiftd.server.util.ServerTimeUtil;
import kohgylw.kiftd.server.util.VariableSpeedBufferedOutputStream;
import org.springframework.util.ObjectUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MultiWriter {

    private static final long DOWNLOAD_CACHE_MAX_AGE = 1800L;
    private static String tempDir = null;
    private static Integer tempBlocks = null;
    private MinioUtil minioUtil = MinioUtil.init();

    public static MultiWriter init() {
        ConfigureReader instance = ConfigureReader.instance();
        tempDir = instance.serverp.getProperty("local.temp.dir");
        String property = instance.serverp.getProperty("buff.size");
        tempBlocks = Integer.valueOf(property);
        return new MultiWriter();
    }


    /**
     * <h2>回传文件数据，可选择是否发送具体的文件内容</h2>
     * <p>
     * 该方法用于提供对文件下载请求的处理，并按照请求方式提供相应的输出流写出。当选择发送具体的文件内容时将会正常返回文件内容， 否则仅返回响应头而无响应体。
     * </p>
     *
     * @param request      javax.servlet.http.HttpServletRequest 请求对象
     * @param response     javax.servlet.http.HttpServletResponse 响应对象
     * @param fo           java.io.File 需要写出的文件
     * @param fname        java.lang.String 文件名
     * @param contentType  java.lang.String HTTP Content-Type类型（用于控制客户端行为）
     * @param maxRate      long 最大输出速率，以B/s为单位，若为负数则不限制输出速率（用于限制客户端的下载速度）
     * @param eTag         java.lang.String 资源的唯一性标识，例如"aabbcc"
     * @param isAttachment boolean 是否作为附件回传，若希望用户下载则应设置为true
     * @param sendBody     boolean 是否发送具体的文件内容，若设置为false，则仅返回响应头
     * @return int 操作结束时返回的状态码
     * @author 青阳龙野(kohgylw)
     */
    public MinioResp writeRangeFile(HttpServletRequest request, HttpServletResponse response, File fo, String fname, String contentType, long maxRate, String eTag, boolean isAttachment, boolean sendBody) throws Exception {
        long fileLength = fo.length();// 文件总大小
        long startOffset = 0; // 起始偏移量
        boolean hasEnd = false;// 请求区间是否存在结束标识
        long endOffset = 0; // 结束偏移量
        long contentLength = 0; // 响应体长度
        String rangeBytes = "";// 请求中的Range参数
        int status = HttpServletResponse.SC_OK;// 初始响应码为200
        // 检查是否有可用的缓存
        String lastModified = ServerTimeUtil.getLastModifiedFormBlock(fo);
        String ifModifiedSince = request.getHeader("If-Modified-Since");
        String ifNoneMatch = request.getHeader("If-None-Match");
        // 是否提供了两个判断参数之一？
        if (ifModifiedSince != null || ifNoneMatch != null) {
            // 是，那么是否提供了Etag？
            if (ifNoneMatch != null) {
                // 是，则只检查Etag，理论上其比Last-Modified更准确
                if (ifNoneMatch.trim().equals(eTag)) {
                    status = HttpServletResponse.SC_NOT_MODIFIED;
                    response.setStatus(status);// 304
                    return new MinioResp(status);
                }
            } else {
                // 不是，则再检查Last-Modified
                if (ifModifiedSince.trim().equals(lastModified)) {
                    status = HttpServletResponse.SC_NOT_MODIFIED;
                    response.setStatus(status);// 304
                    return new MinioResp(status);
                }
            }
        }
        // 检查断点续传请求是否过期，两个条件，有就要满足，没有就算了
        String ifUnmodifiedSince = request.getHeader("If-Unmodified-Since");
        if (ifUnmodifiedSince != null && !(ifUnmodifiedSince.trim().equals(lastModified))) {
            status = HttpServletResponse.SC_PRECONDITION_FAILED;
            response.setStatus(status);// 412
            return new MinioResp(status);
        }
        String ifMatch = request.getHeader("If-Match");
        if (ifMatch != null && !(ifMatch.trim().equals(eTag))) {
            status = HttpServletResponse.SC_PRECONDITION_FAILED;
            response.setStatus(status);// 412
            return new MinioResp(status);
        }
        // 设置请求头，基于kiftd文件系统推荐使用application/octet-stream
        response.setContentType(contentType);
        // 设置文件信息
        response.setCharacterEncoding("UTF-8");
        // 设置Content-Disposition信息
        if (isAttachment) {
            response.setHeader("Content-Disposition", "attachment; filename=\"" + EncodeUtil.getFileNameByUTF8(fname)
                    + "\"; filename*=utf-8''" + EncodeUtil.getFileNameByUTF8(fname));
        } else {
            response.setHeader("Content-Disposition", "inline");
        }
        // 设置支持断点续传功能
        response.setHeader("Accept-Ranges", "bytes");
        // 设置缓存控制信息
        response.setHeader("ETag", eTag);
        response.setHeader("Last-Modified", ServerTimeUtil.getLastModifiedFormBlock(fo));
        response.setHeader("Cache-Control", "max-age=" + DOWNLOAD_CACHE_MAX_AGE);
        // 针对具备断点续传性质的请求进行解析
        final String rangeTag = request.getHeader("Range");
        final String ifRange = request.getHeader("If-Range");
        if (rangeTag != null && rangeTag.startsWith("bytes=")
                && (ifRange == null || ifRange.trim().equals(eTag) || ifRange.trim().equals(lastModified))) {
            status = HttpServletResponse.SC_PARTIAL_CONTENT;
            response.setStatus(status);
            rangeBytes = rangeTag.replaceAll("bytes=", "");
            if (rangeBytes.endsWith("-")) {
                // 解析请求参数范围为仅有起始偏移量而无结束偏移量的情况
                startOffset = Long.parseLong(rangeBytes.substring(0, rangeBytes.indexOf('-')).trim());
                // 仅具备起始偏移量时，例如文件长为13，请求为5-，则响应体长度为8
                contentLength = fileLength - startOffset;
            } else {
                hasEnd = true;
                startOffset = Long.parseLong(rangeBytes.substring(0, rangeBytes.indexOf('-')).trim());
                endOffset = Long.parseLong(rangeBytes.substring(rangeBytes.indexOf('-') + 1).trim());
                // 具备起始偏移量与结束偏移量时，例如0-9，则响应体长度为10个字节
                contentLength = endOffset - startOffset + 1;
            }
            // 设置Content-Range，格式为“bytes 起始偏移-结束偏移/文件的总大小”
            String contentRange;
            if (!hasEnd) {
                contentRange = new StringBuffer("bytes ").append("" + startOffset).append("-")
                        .append("" + (fileLength - 1)).append("/").append("" + fileLength).toString();
            } else {
                contentRange = new StringBuffer("bytes ").append(rangeBytes).append("/").append("" + fileLength)
                        .toString();
            }
            response.setHeader("Content-Range", contentRange);
        } else { // 从开始进行下载
            contentLength = fileLength; // 客户端要求全文下载
        }
        if (sendBody) {
            response.setHeader("Content-Length", "" + contentLength);// 设置请求体长度
        } else {
            response.setHeader("Content-Length", "0");
        }

        Thread.sleep(1 * 1000);
        String bucket = UUID.randomUUID().toString()
                .replace("-", "");
        String cacheFile = tempDir + File.separator + "mino." + bucket + "." + fo.getName();
        if (sendBody) {
            File tempFile = new File(cacheFile);
            minioUtil.createBucket(bucket);
            minioUtil.uploadFile(bucket, tempFile.getName(), new FileInputStream(fo), fo.length());
            List<ObjectItem> objectItems = minioUtil.listBucket(bucket);
            File minioCache = minioUtil.getFile(bucket, objectItems.get(0).getObjectName(), tempFile);
            // 写出缓冲
            byte[] buf = new byte[ConfigureReader.instance().getBuffSize()];
            // 读取文件并写处至输出流
            try (RandomAccessFile raf = new RandomAccessFile(minioCache, "r")) {
                BufferedOutputStream out = maxRate >= 0
                        ? new VariableSpeedBufferedOutputStream(response.getOutputStream(), maxRate,
                        request.getSession())
                        : new BufferedOutputStream(response.getOutputStream());
                raf.seek(startOffset);
                if (!hasEnd) {
                    // 无结束偏移量时，将其从起始偏移量开始写到文件整体结束，如果从头开始下载，起始偏移量为0
                    int n = 0;
                    while ((n = raf.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                } else {
                    // 有结束偏移量时，将其从起始偏移量开始写至指定偏移量结束。
                    int n = 0;
                    long readLength = 0;// 写出量，用于确定结束位置
                    while (readLength < contentLength) {
                        n = raf.read(buf);
                        readLength += n;
                        out.write(buf, 0, (int) (readLength <= contentLength ? n : n - (readLength - contentLength)));
                    }
                }
                out.flush();
                out.close();
            } catch (IOException | IndexOutOfBoundsException ex) {
                // 针对任何IO异常忽略，传输失败不处理
                status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            } catch (IllegalArgumentException e) {
                status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                try {
                    response.sendError(status);
                } catch (IOException e1) {
                }
            }
        }
        return new MinioResp(bucket, cacheFile, status);
    }
}
