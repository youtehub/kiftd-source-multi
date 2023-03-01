package kohgylw.kiftd.multithreading.minio;

import io.minio.ObjectWriteResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class FutureResponse {

    private long[] partInfo;
    private ObjectWriteResponse writeResponse;

}
