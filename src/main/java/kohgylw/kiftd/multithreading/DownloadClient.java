package kohgylw.kiftd.multithreading;

import kohgylw.kiftd.server.util.ConfigureReader;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

public class DownloadClient implements Runnable {

    final File fo;
    final String tempFilePath;
    final Long start;
    final Long end;
    final Long finalCurrentLength;

    public DownloadClient(File fo, String tempFilePath, Long start, Long end, Long finalCurrentLength) {
        this.fo = fo;
        this.tempFilePath = tempFilePath;
        this.start = start;
        this.end = end;
        this.finalCurrentLength = finalCurrentLength;
    }

    @Override
    public void run() {
        File execute = new File(tempFilePath);
        // 读取文件并写处至输出流
        try (RandomAccessFile raf = new RandomAccessFile(fo, "rws")) {
            BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(execute));
            raf.seek(start);
            // 有结束偏移量时，将其从起始偏移量开始写至指定偏移量结束。
            int num = 0;
            while (raf.getFilePointer() <= end) {
                byte[] buf = new byte[ConfigureReader.instance().getBuffSize()];
                num = raf.read(buf);
                if (num == -1) break;
                fos.write(buf);
            }
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public File getFo() {
        return fo;
    }


    public String getTempFilePath() {
        return tempFilePath;
    }


    public Long getStart() {
        return start;
    }


    public Long getFinalCurrentLength() {
        return finalCurrentLength;
    }

    @Override
    public String toString() {
        return "DownloadClient{" +
                "fo=" + fo +
                ", tempFilePath='" + tempFilePath + '\'' +
                ", start=" + start +
                ", end=" + end +
                ", finalCurrentLength=" + finalCurrentLength +
                '}';
    }
}
