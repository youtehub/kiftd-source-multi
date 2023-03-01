package kohgylw.kiftd.multithreading.minio;

import java.io.File;
import java.util.UUID;

public class MinioResp {

    private String bucket;
    private String fileName;
    private int status;

    public MinioResp(int status) {
        this.status = status;
    }

    public MinioResp(String bucket, String fileName, int status) {
        this.bucket = bucket;
        this.fileName = fileName;
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public String getBucket() {
        return bucket;
    }

    public String getFileName() {
        return fileName;
    }
}
