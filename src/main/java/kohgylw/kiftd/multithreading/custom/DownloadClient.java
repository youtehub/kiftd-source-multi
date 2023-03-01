//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package kohgylw.kiftd.multithreading.custom;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import kohgylw.kiftd.server.util.ConfigureReader;

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

    public void run() {
        File execute = new File(this.tempFilePath);

        try {
            RandomAccessFile raf = new RandomAccessFile(this.fo, "rws");
            Throwable var3 = null;

            try {
                BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(execute));
                raf.seek(this.start);
                while(raf.getFilePointer() <= this.end) {
                    byte[] buf = new byte[ConfigureReader.instance().getBuffSize()];
                    int num = raf.read(buf);
                    if (num == -1) {
                        break;
                    }

                    fos.write(buf);
                }

                fos.flush();
                fos.close();
            } catch (Throwable var15) {
                var3 = var15;
                throw var15;
            } finally {
                if (raf != null) {
                    if (var3 != null) {
                        try {
                            raf.close();
                        } catch (Throwable var14) {
                            var3.addSuppressed(var14);
                        }
                    } else {
                        raf.close();
                    }
                }

            }
        } catch (Exception var17) {
            var17.printStackTrace();
        }

    }

    public File getFo() {
        return this.fo;
    }

    public String getTempFilePath() {
        return this.tempFilePath;
    }

    public Long getStart() {
        return this.start;
    }

    public Long getFinalCurrentLength() {
        return this.finalCurrentLength;
    }

}
