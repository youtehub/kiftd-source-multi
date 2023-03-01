package kohgylw.kiftd.multithreading;


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
        return future;
    }

    public void setFuture(CompletableFuture<File> future) {
        this.future = future;
    }

    public long getCurrentLength() {
        return currentLength;
    }

    public void setCurrentLength(long currentLength) {
        this.currentLength = currentLength;
    }
}
