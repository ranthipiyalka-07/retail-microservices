import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Creates a private CA and one certificate per component under docker/tls. Run from the repo root: java GenTls.java */
public class GenTls {

    /** component name (also its DNS name in Docker) -> HTTPS port; 0 = not a Spring service */
    static final Map<String, Integer> ENTITIES = new LinkedHashMap<>();
    static {
        ENTITIES.put("config-server", 8888);
        ENTITIES.put("eureka-server", 8761);
        ENTITIES.put("api-gateway", 8080);
        ENTITIES.put("auth-service", 8081);
        ENTITIES.put("customer-service", 8082);
        ENTITIES.put("product-service", 8083);
        ENTITIES.put("inventory-service", 8084);
        ENTITIES.put("cart-service", 8085);
        ENTITIES.put("order-service", 8086);
        ENTITIES.put("payment-service", 8087);
        ENTITIES.put("review-service", 8088);
        ENTITIES.put("campaign-service", 8089);
        ENTITIES.put("support-service", 8090);
        ENTITIES.put("wishlist-service", 8091);
        ENTITIES.put("notification-service", 8092);
        ENTITIES.put("mysql", 0);
        ENTITIES.put("kafka", 0);
        ENTITIES.put("dev-client", 0);          // your own identity for browsers and PowerShell
    }

    static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    public static void main(String[] args) throws Exception {
        boolean force = List.of(args).contains("--force");
        Path caDir = Path.of("docker", "tls", "ca");
        Path outDir = Path.of("docker", "tls", "out");
        if (Files.exists(caDir.resolve("ca.p12")) && !force) {
            System.err.println("Certificates already exist in docker/tls. Run with --force to replace all of them.");
            System.exit(1);
        }
        String pw = storePassword(force);
        delete(caDir);
        delete(outDir);
        Files.createDirectories(caDir);
        Files.createDirectories(outDir);

        keytool("-genkeypair", "-alias", "ca", "-keyalg", "RSA", "-keysize", "3072", "-sigalg", "SHA384withRSA",
                "-validity", "3650", "-dname", "CN=Retail Dev CA,O=Retail Dev",
                "-ext", "bc:c=ca:true", "-ext", "ku:c=keyCertSign,cRLSign",
                "-keystore", caDir.resolve("ca.p12"), "-storetype", "PKCS12", "-storepass", pw, "-keypass", pw);
        keytool("-exportcert", "-alias", "ca", "-rfc", "-file", caDir.resolve("ca.crt"),
                "-keystore", caDir.resolve("ca.p12"), "-storepass", pw);

        for (Map.Entry<String, Integer> entity : ENTITIES.entrySet()) {
            System.out.println("Issuing " + entity.getKey() + " ...");
            issue(entity.getKey(), entity.getValue(), caDir, outDir.resolve(entity.getKey()), pw);
        }
        makeReadable(Path.of("docker", "tls"));
        System.out.println();
        System.out.println("Done. The CA certificate for your machine is docker/tls/ca/ca.crt");
        System.out.println("The store password is TLS_STORE_PASSWORD in .env. Valid for 365 days.");
    }

    static void issue(String name, int port, Path caDir, Path dir, String pw) throws Exception {
        Files.createDirectories(dir);
        Path keystore = dir.resolve("keystore.p12");
        Path caCrt = caDir.resolve("ca.crt");
        Path csr = dir.resolve(name + ".csr");
        Path crt = dir.resolve(name + ".crt");
        String san = "SAN=dns:" + name + ",dns:localhost,ip:127.0.0.1";

        keytool("-genkeypair", "-alias", name, "-keyalg", "RSA", "-keysize", "2048", "-sigalg", "SHA256withRSA",
                "-validity", "365", "-dname", "CN=" + name + ",O=Retail Dev", "-ext", san,
                "-keystore", keystore, "-storetype", "PKCS12", "-storepass", pw, "-keypass", pw);
        keytool("-certreq", "-alias", name, "-keystore", keystore, "-storepass", pw, "-file", csr);
        keytool("-gencert", "-alias", "ca", "-keystore", caDir.resolve("ca.p12"), "-storepass", pw,
                "-infile", csr, "-outfile", crt, "-rfc", "-validity", "365", "-sigalg", "SHA256withRSA",
                "-ext", san, "-ext", "EKU=serverAuth,clientAuth", "-ext", "KU:c=digitalSignature,keyEncipherment");
        keytool("-importcert", "-alias", "ca", "-file", caCrt, "-keystore", keystore, "-storepass", pw, "-noprompt");
        keytool("-importcert", "-alias", name, "-file", crt, "-keystore", keystore, "-storepass", pw, "-noprompt");
        keytool("-importcert", "-alias", "ca", "-file", caCrt, "-keystore", dir.resolve("truststore.p12"),
                "-storetype", "PKCS12", "-storepass", pw, "-noprompt");
        Files.delete(csr);
        Files.delete(crt);

        // PEM copies: curl in the health checks, and MySQL, read PEM files
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keystore)) {
            ks.load(in, pw.toCharArray());
        }
        PrivateKey key = (PrivateKey) ks.getKey(name, pw.toCharArray());
        Certificate cert = ks.getCertificate(name);
        pem(dir.resolve("tls.key"), "PRIVATE KEY", key.getEncoded());
        pem(dir.resolve("tls.crt"), "CERTIFICATE", cert.getEncoded());
        Files.copy(caCrt, dir.resolve("ca.crt"));

        if (port > 0) {                                  // a Spring service
            write(dir.resolve("jvm.args"), String.join("\n",
                    "-Djavax.net.ssl.keyStore=/certs/keystore.p12",
                    "-Djavax.net.ssl.keyStoreType=PKCS12",
                    "-Djavax.net.ssl.keyStorePassword=" + pw,
                    "-Djavax.net.ssl.trustStore=/certs/truststore.p12",
                    "-Djavax.net.ssl.trustStoreType=PKCS12",
                    "-Djavax.net.ssl.trustStorePassword=" + pw, ""));
            String auth = name.equals("config-server") ? " -u \"$SPRING_SECURITY_USER_NAME:$SPRING_SECURITY_USER_PASSWORD\"" : "";
            write(dir.resolve("healthcheck.sh"), "#!/bin/sh\ncurl -fsS --max-time 5 --cacert /certs/ca.crt "
                    + "--cert /certs/tls.crt --key /certs/tls.key" + auth + " https://localhost:" + port
                    + "/actuator/health > /dev/null\n");
        }
        if (name.equals("kafka")) {                      // for the broker health check and kafka-topics.sh
            write(dir.resolve("client.properties"), String.join("\n",
                    "security.protocol=SSL",
                    "ssl.truststore.location=/certs/truststore.p12", "ssl.truststore.type=PKCS12",
                    "ssl.truststore.password=" + pw,
                    "ssl.keystore.location=/certs/keystore.p12", "ssl.keystore.type=PKCS12",
                    "ssl.keystore.password=" + pw, "ssl.key.password=" + pw, ""));
        }
    }

    static String storePassword(boolean force) throws IOException {
        Path env = Path.of(".env");
        List<String> lines = Files.exists(env) ? new ArrayList<>(Files.readAllLines(env)) : new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("TLS_STORE_PASSWORD=") && line.length() > 19 && !force) {
                return line.substring(19).trim();
            }
        }
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        String pw = HexFormat.of().formatHex(bytes);
        lines.removeIf(l -> l.startsWith("TLS_STORE_PASSWORD="));
        lines.add("TLS_STORE_PASSWORD=" + pw);
        Files.write(env, lines, StandardCharsets.US_ASCII);
        return pw;
    }

    static void keytool(Object... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", WINDOWS ? "keytool.exe" : "keytool").toString());
        for (Object arg : args) {
            command.add(arg.toString());
        }
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException("keytool " + args[0] + " failed:\n" + output);   // the command line holds the password: not printed
        }
    }

    static void pem(Path file, String type, byte[] der) throws IOException {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
        write(file, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
    }

    static void write(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.US_ASCII);
    }

    /** Containers run as other users: the files must be readable. Development only. */
    static void makeReadable(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.forEach(p -> {
                p.toFile().setReadable(true, false);
                if (Files.isDirectory(p)) {
                    p.toFile().setExecutable(true, false);
                }
            });
        }
    }

    static void delete(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
            }
        }
    }
}