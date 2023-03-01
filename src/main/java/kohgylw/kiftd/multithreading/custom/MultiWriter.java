//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package kohgylw.kiftd.multithreading.custom;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import kohgylw.kiftd.multithreading.DownloadClient;
import kohgylw.kiftd.multithreading.PartSizeUtil;
import kohgylw.kiftd.server.util.ConfigureReader;
import kohgylw.kiftd.server.util.EncodeUtil;
import kohgylw.kiftd.server.util.ServerTimeUtil;
import kohgylw.kiftd.server.util.VariableSpeedBufferedOutputStream;
import org.springframework.util.ObjectUtils;

public class MultiWriter {
    private static final long DOWNLOAD_CACHE_MAX_AGE = 1800L;
    private static String tempDir = null;
    private static Integer tempBlocks = null;

    public MultiWriter() {
    }

    public static MultiWriter init() {
        ConfigureReader instance = ConfigureReader.instance();
        tempDir = instance.serverp.getProperty("local.temp.dir");
        String property = instance.serverp.getProperty("buff.size");
        tempBlocks = Integer.valueOf(property);
        return new MultiWriter();
    }

    public static int writeRangeFile(HttpServletRequest request, HttpServletResponse response, File fo, String fname, String contentType, long maxRate, String eTag, boolean isAttachment, boolean sendBody) throws Exception {
        long fileLength = fo.length();
        long startOffset = 0L;
        boolean hasEnd = false;
        long endOffset = 0L;
        long contentLength = 0L;
        String rangeBytes = "";
        int status = 200;
        String lastModified = ServerTimeUtil.getLastModifiedFormBlock(fo);
        String ifModifiedSince = request.getHeader("If-Modified-Since");
        String ifNoneMatch = request.getHeader("If-None-Match");
        if (ifModifiedSince != null || ifNoneMatch != null) {
            if (ifNoneMatch != null) {
                if (ifNoneMatch.trim().equals(eTag)) {
                    status = 304;
                    response.setStatus(status);
                    return status;
                }
            } else if (ifModifiedSince.trim().equals(lastModified)) {
                status = 304;
                response.setStatus(status);
                return status;
            }
        }

        String ifUnmodifiedSince = request.getHeader("If-Unmodified-Since");
        if (ifUnmodifiedSince != null && !ifUnmodifiedSince.trim().equals(lastModified)) {
            status = 412;
            response.setStatus(status);
            return status;
        } else {
            String ifMatch = request.getHeader("If-Match");
            if (ifMatch != null && !ifMatch.trim().equals(eTag)) {
                status = 412;
                response.setStatus(status);
                return status;
            } else {
                response.setContentType(contentType);
                response.setCharacterEncoding("UTF-8");
                if (isAttachment) {
                    response.setHeader("Content-Disposition", "attachment; filename=\"" + EncodeUtil.getFileNameByUTF8(fname) + "\"; filename*=utf-8''" + EncodeUtil.getFileNameByUTF8(fname));
                } else {
                    response.setHeader("Content-Disposition", "inline");
                }

                response.setHeader("Accept-Ranges", "bytes");
                response.setHeader("ETag", eTag);
                response.setHeader("Last-Modified", ServerTimeUtil.getLastModifiedFormBlock(fo));
                response.setHeader("Cache-Control", "max-age=1800");
                String rangeTag = request.getHeader("Range");
                String ifRange = request.getHeader("If-Range");
                String fileName;
                if (rangeTag != null && rangeTag.startsWith("bytes=") && (ifRange == null || ifRange.trim().equals(eTag) || ifRange.trim().equals(lastModified))) {
                    status = 206;
                    response.setStatus(status);
                    rangeBytes = rangeTag.replaceAll("bytes=", "");
                    if (rangeBytes.endsWith("-")) {
                        startOffset = Long.parseLong(rangeBytes.substring(0, rangeBytes.indexOf(45)).trim());
                        contentLength = fileLength - startOffset;
                    } else {
                        hasEnd = true;
                        startOffset = Long.parseLong(rangeBytes.substring(0, rangeBytes.indexOf(45)).trim());
                        endOffset = Long.parseLong(rangeBytes.substring(rangeBytes.indexOf(45) + 1).trim());
                        contentLength = endOffset - startOffset + 1L;
                    }

                    if (!hasEnd) {
                        fileName = "bytes " + "" + startOffset + "-" + "" + (fileLength - 1L) + "/" + "" + fileLength;
                    } else {
                        fileName = "bytes " + rangeBytes + "/" + "" + fileLength;
                    }

                    response.setHeader("Content-Range", fileName);
                } else {
                    contentLength = fileLength;
                }

                if (sendBody) {
                    response.setHeader("Content-Length", "" + contentLength);
                } else {
                    response.setHeader("Content-Length", "0");
                }

                if (sendBody) {
                    contentLength = startOffset == 0L ? fo.length() : fo.length() - startOffset;
                    fileName = fo.getName();
                    int threadNum = PartSizeUtil.partSize(contentLength, tempBlocks);
                    ExecutorService executorService = Executors.newFixedThreadPool(threadNum);
                    long blockSize = contentLength / (long) threadNum;
                    List<kohgylw.kiftd.multithreading.DownloadClient> downloadList = new ArrayList();
                    long offSet = 0L;

                    for (int index = 0; index < threadNum; ++index) {
                        long start = offSet;
                        long end = offSet + blockSize;
                        long currentLength = blockSize;
                        if (index == threadNum - 1) {
                            end = contentLength;
                            currentLength = contentLength - offSet;
                        }

                        String tempFilePath = tempDir + File.separator + "." + fileName + ".download." + index;
                        offSet += currentLength;
                        kohgylw.kiftd.multithreading.DownloadClient dc = new kohgylw.kiftd.multithreading.DownloadClient(fo, tempFilePath, start, end, currentLength);
                        ((List) downloadList).add(dc);
                    }

                    Iterator var65 = ((List) downloadList).iterator();

                    while (var65.hasNext()) {
                        kohgylw.kiftd.multithreading.DownloadClient downloadClient = (kohgylw.kiftd.multithreading.DownloadClient) var65.next();
                        executorService.submit(downloadClient);
                    }

                    String pToFileName = tempDir + File.separator + fileName;
                    File dscfile = new File(pToFileName);
                    Integer index = 0;
                    Integer lastSize = 0;
                    RandomAccessFile temRaf = new RandomAccessFile(dscfile, "rw");

                    while (true) {
                        kohgylw.kiftd.multithreading.DownloadClient download;
                        File sourceFile;
                        do {
                            if (((List) downloadList).size() <= 0) {
                                temRaf.close();
                                executorService.shutdown();
                                byte[] buf = new byte[ConfigureReader.instance().getBuffSize()];

                                try {
                                    RandomAccessFile raf = new RandomAccessFile(fo, "r");
                                    Throwable var74 = null;

                                    try {
                                        BufferedOutputStream out = maxRate >= 0L ? new VariableSpeedBufferedOutputStream(response.getOutputStream(), maxRate, request.getSession()) : new BufferedOutputStream(response.getOutputStream());
                                        raf.seek(0L);
                                        int n;
                                        if (!hasEnd) {

                                            while ((n = raf.read(buf)) != -1) {
                                                ((BufferedOutputStream) out).write(buf, 0, n);
                                            }
                                        } else {
                                            long readLength = 0L;

                                            while (readLength < contentLength) {
                                                n = raf.read(buf);
                                                readLength += (long) n;
                                                ((BufferedOutputStream) out).write(buf, 0, (int) (readLength <= contentLength ? (long) n : (long) n - (readLength - contentLength)));
                                            }
                                        }

                                        ((BufferedOutputStream) out).flush();
                                        ((BufferedOutputStream) out).close();
                                        return status;
                                    } catch (Throwable var60) {
                                        var74 = var60;
                                        throw var60;
                                    } finally {
                                        if (raf != null) {
                                            if (var74 != null) {
                                                try {
                                                    raf.close();
                                                } catch (Throwable var59) {
                                                    var74.addSuppressed(var59);
                                                }
                                            } else {
                                                raf.close();
                                            }
                                        }

                                    }
                                } catch (IndexOutOfBoundsException | IOException var62) {
                                    status = 500;
                                    return status;
                                } catch (IllegalArgumentException var63) {
                                    status = 500;

                                    try {
                                        response.sendError(status);
                                    } catch (IOException var58) {
                                    }

                                    return status;
                                }
                            }

                            download = (DownloadClient) ((List) downloadList).get(index);
                            String tempFilePath = download.getTempFilePath();
                            sourceFile = new File(tempFilePath);
                        } while (ObjectUtils.isEmpty(sourceFile));
                        // 写出缓冲
                        byte[] buf = new byte[ConfigureReader.instance().getBuffSize()];
                        // 读取文件并写处至输出流
                        try (RandomAccessFile raf = new RandomAccessFile(dscfile, "r")) {
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
                }
            }
        }
        return status;
    }
}
