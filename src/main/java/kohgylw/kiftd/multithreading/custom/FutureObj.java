//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package kohgylw.kiftd.multithreading.custom;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class FutureObj {
    private CompletableFuture<File> future;
    private long currentLength;

    public FutureObj(CompletableFuture<File> future, long currentLength) {
        this.future = future;
        this.currentLength = currentLength;
    }

    public CompletableFuture<File> getFuture() {
        return this.future;
    }

    public void setFuture(CompletableFuture<File> future) {
        this.future = future;
    }

    public long getCurrentLength() {
        return this.currentLength;
    }

    public void setCurrentLength(long currentLength) {
        this.currentLength = currentLength;
    }
}
