import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/** 通用 HTTPS 下载器（JVM 出口稳定）：java tools\Dl.java <url> <outFile> */
public class Dl {
    public static void main(String[] args) throws Exception {
        String url = args[0];
        Path out = Path.of(args[1]);
        Files.createDirectories(out.toAbsolutePath().getParent());
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "Mozilla/5.0")
                .GET().build();
        HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            System.err.println("HTTP " + res.statusCode() + " for " + url);
            System.exit(1);
        }
        long total = res.headers().firstValueAsLong("Content-Length").orElse(-1);
        long got = 0;
        try (InputStream in = res.body();
             OutputStream fos = Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
                got += n;
                if (total > 0) {
                    System.out.print("\r" + out.getFileName() + " " + (got / 1048576) + "/" + (total / 1048576) + " MB   ");
                }
            }
        }
        System.out.println("\nDONE " + out + " bytes=" + got);
    }
}
