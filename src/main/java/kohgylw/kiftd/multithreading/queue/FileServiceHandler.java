package kohgylw.kiftd.multithreading.queue;

import cn.hutool.core.util.StrUtil;
import kohgylw.kiftd.multithreading.MultiWriter;
import kohgylw.kiftd.multithreading.PartSizeUtil;
import kohgylw.kiftd.multithreading.minio.MinioResp;
import kohgylw.kiftd.server.util.ConfigureReader;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.UUID;

public class FileServiceHandler {

    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final File fo;
    private final String fname;
    private final String contentType;
    private final long maxRate;
    private final String eTag;
    private final boolean isAttachment;
    private final boolean sendBody;
    private MinioResp minioResp;

    public FileServiceHandler(HttpServletRequest request, HttpServletResponse response, File fo, String fname, String contentType, long maxRate, String eTag, boolean isAttachment, boolean sendBody) {
        this.request = request;
        this.response = response;
        this.fo = fo;
        this.fname = fname;
        this.contentType = contentType;
        this.maxRate = maxRate;
        this.eTag = eTag;
        this.isAttachment = isAttachment;
        this.sendBody = sendBody;
        this.minioResp = null;
    }


    // 这里也就是我们实现QueueTaskHandler的处理接口
    public void run() {
        try {
            minioResp = MultiWriter.init().writeRangeFile(request, response, fo, fname,
                    contentType, maxRate, eTag, isAttachment, sendBody);
            boolean minioCache = StrUtil.isNotEmpty(minioResp.getFileName()) && StrUtil.isNotEmpty(minioResp.getBucket());
            if (minioCache) {
                PartSizeUtil.clearCache(minioResp,null);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public Integer getStatus() {
        return minioResp.getStatus();
    }
}
