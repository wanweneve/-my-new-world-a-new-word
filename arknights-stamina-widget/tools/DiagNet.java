import java.io.InputStream;
import java.net.URI;
import java.net.HttpURLConnection;

public class DiagNet {
    public static void main(String[] args) throws Exception {
        String[] urls = {
            "https://dl.google.com/android/repository/repository2-1.xml",
            "https://mirrors.huaweicloud.com/gradle/gradle-9.5.1-bin.zip",
            "https://repo.maven.apache.org/maven2/",
            "https://services.gradle.org/distributions/"
        };
        for (String u : urls) {
            try {
                HttpURLConnection c = (HttpURLConnection) URI.create(u).toURL().openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(12000);
                c.setReadTimeout(12000);
                int code = c.getResponseCode();
                System.out.println("OK  " + code + " " + u);
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                if (in != null) { in.read(new byte[512]); in.close(); }
                c.disconnect();
            } catch (Exception e) {
                System.out.println("ERR " + u + " -> " + e);
            }
        }
    }
}
