package com.github.ancalled;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import javax.net.ssl.KeyManagerFactory;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.security.*;
import java.security.cert.CertificateException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Updater {

    public static final Logger log = Logger.getLogger(Updater.class);

    public static final String USER_DIR = System.getProperty("user.dir");
    public static final String TEMPORAL_FOLDER = USER_DIR + File.separator + "tmp";
    public static final String SEP = File.separator;
    public static final String DEFAULT_CONFIG = USER_DIR + SEP + "updater.properties";

    private static final NumberFormat format = new DecimalFormat("###,###,###");
    public static final int TIMEOUT = 5000;


    public static final String UPDATE_SERVER_SCHEME = "update.server.scheme";
    public static final String UPDATE_SERVER_HOST = "update.server.host";
    public static final String UPDATE_SERVER_PORT = "update.server.port";
    public static final String UPDATE_LEVEL = "update.level";
    public static final String TERMINAL_PATH = "update.path";
    public static final String APPLICATION_HOME = "application.home";
    public static final String KEYSTORE_PATH = "keystore.path";
    public static final String KEYSTORE_PASSWORD = "keystore.pwd";
    public static final String IGNORE_VER = "ignore.version";
    public static final String APP_JAR_PATH = "app.jar.path";

    public static enum UpdateLevel {
        MAJOR, MINOR, TEST;

        public static UpdateLevel getLevel(String lvl) {
            for (UpdateLevel l : UpdateLevel.values()) {
                if (l.toString().equalsIgnoreCase(lvl)) {
                    return l;
                }
            }
            return null;
        }
    }


    private final HttpParams httpParameters;

    private final Map<String, String> changes = new HashMap<>();

    public final int currentVersion;
    private int lastVersion;
    private int ignoreVersion;

    private final String scheme;
    private final String host;
    private final Integer port;

    private final String keyStorePath;
    private final String keyStorePwd;
    public final String terminalFolderPh;
    public final String lastBuildsInfoPh;

    public Updater(Properties props, UpdateLevel level) {
        scheme = props.getProperty(UPDATE_SERVER_SCHEME);
        host = props.getProperty(UPDATE_SERVER_HOST);
        port = Integer.parseInt(props.getProperty(UPDATE_SERVER_PORT, "80"));

        terminalFolderPh = "/" + props.getProperty(TERMINAL_PATH);
        lastBuildsInfoPh = terminalFolderPh + "/last-build";

        String appJarPath = props.getProperty(APP_JAR_PATH);
        String ignoreVersionStr = props.getProperty(IGNORE_VER, "0");

        ignoreVersion = Integer.parseInt(ignoreVersionStr);

        if (level == null) {
            String lvlStr = props.getProperty(UPDATE_LEVEL);
            level = UpdateLevel.getLevel(lvlStr);
            if (level == null) {
                level = UpdateLevel.MINOR;
            }
        }

        File tempFolder = new File(TEMPORAL_FOLDER);
        if (!tempFolder.exists()) {
            tempFolder.mkdir();
        }

        clearTempDir(tempFolder);


        String applicationHome = props.getProperty(APPLICATION_HOME, USER_DIR);

        currentVersion = Version.getVersion(appJarPath).getBuildNumber();

        keyStorePath = props.getProperty(KEYSTORE_PATH);
        keyStorePwd = props.getProperty(KEYSTORE_PASSWORD);

        log.info("Update server: " + scheme + "://" + host + ":" + port);
        log.info("Download path: " + applicationHome);
        log.info("Current version: " + currentVersion);
        log.info("Update level: " + level);

        httpParameters = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParameters, TIMEOUT);
        HttpConnectionParams.setSoTimeout(httpParameters, TIMEOUT);
    }

    private void clearTempDir(File tempFolder) {
        File[] files = tempFolder.listFiles();

        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
    }

    public void update() {
        if (currentVersion == 0) {
            log.warn("Current version is 0, maybe application cant determine yore version");
            log.warn("Updating new version will stop");
            return;
        }

        log.info("Updating...");
        harvestChanges();
        downloadChanges();
        log.info("Update was completed successfully.");

        try {
            replaceOldFiles();
        } catch (IOException e) {
            log.warn("Some thing wrong with replacing updated files");
            e.printStackTrace();
        }
    }

    public boolean checkNewVersions() {
        log.info("Checking new version");
        DefaultHttpClient client = new DefaultHttpClient();

        try {
            HttpResponse response = client.execute(new HttpGet(
                    encode(scheme, host, port, lastBuildsInfoPh)));

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                String verStr = EntityUtils.toString(response.getEntity());
                lastVersion = Integer.parseInt(verStr.trim());

                log.info("Last version is " + lastVersion);
            }

        } catch (IOException | URISyntaxException e) {
            log.warn(e.getMessage());
            log.warn("Cant get last version, network problems probably");
        } finally {
            client.getConnectionManager().shutdown();
        }
        return currentVersion < lastVersion && lastVersion != ignoreVersion;
    }

    private void harvestChanges() {
        log.info("Harvesting changes");

        if (currentVersion != 0 && lastVersion != 0 &&
                currentVersion != lastVersion) {
            //calculating changes between current and last versions
            for (int ver = currentVersion + 1; ver <= lastVersion; ver++) {

                String verPath = terminalFolderPh + "/" + ver;
                String changesPath = verPath + "/changes";

                log.debug("Got change " + changesPath);

                String content = callResource(changesPath);
                if (content != null) {
                    Map<String,String> changeMap = Arrays.stream(content.split("\\n"))
                            .filter(ch -> ch.startsWith("./") && ch.length() > 1)
                            .map(ch -> ch.substring(2, ch.length()))
                            .collect(Collectors.toMap(c -> c, c -> verPath + "/" + c));

                    changes.putAll(changeMap);
                }
            }
        } else {
            log.info("Harvesting stopped because");
            log.info("current version is " + currentVersion + "/n" +
                    "last version version is " + lastVersion);
        }
    }

    private String callResource(String resource) {

        DefaultHttpClient client = new DefaultHttpClient();
        installSSL(client);

        try {
            HttpResponse response = client.execute(new HttpGet(encode(scheme, host, port, resource)));
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                return EntityUtils.toString(response.getEntity());
            }
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        } finally {
            client.getConnectionManager().shutdown();
        }
        return null;
    }

    private void downloadChanges() {
//        final LoadingForm lf = new LoadingForm();

        Executors.newSingleThreadExecutor().execute(() -> {

            Set<String> keys = changes.keySet();

            for (String key : keys) {
                String path = changes.get(key);
                String filename = TEMPORAL_FOLDER + File.separator + key;

                log.info("Downloading " + path + "...");
                long startTime = System.currentTimeMillis();

                int bytes = downloadResource(path, filename);

                long endTime = System.currentTimeMillis();
                long processTime = endTime - startTime;

                log.info("Ok. Got " + format.format(bytes) + " bytes in " + format.format(processTime) + " milis");
            }

//            Platform.runLater(() -> lf.closeMe());
        });

//        lf.showMe();

    }

    private void replaceOldFiles() throws IOException {
        File tempDir = new File(TEMPORAL_FOLDER);
        copyAllRecursive(tempDir);
    }

    private void copyAllRecursive(File tempDir) throws IOException {
        File[] allFiles = tempDir.listFiles();
        if (allFiles != null) {

            for (File from : allFiles) {
                if (from.isDirectory()) {
                    copyAllRecursive(from);
                    continue;
                }

                String toFilePath = from.getPath().
                        substring(
                                from.getPath().indexOf(TEMPORAL_FOLDER) + TEMPORAL_FOLDER.length(),
                                from.getPath().length()
                        );

                File to = new File(USER_DIR + "/" + toFilePath);
                File parent = new File(to.getParent());

                if (!parent.exists()) {
                    parent.mkdirs();
                }

                try (
                        FileChannel in = new FileInputStream(from).getChannel();
                        FileChannel out = new FileOutputStream(to).getChannel()) {
                    out.transferFrom(in, 0, in.size());
                }
            }
        }
    }


    private int downloadResource(String resource, String toFile) {
        DefaultHttpClient client = new DefaultHttpClient(httpParameters);

        try {
            return downloadResource(client, encode(scheme, host, port, resource), toFile);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        } finally {
            client.getConnectionManager().shutdown();
        }

        return 0;
    }

    private void installSSL(DefaultHttpClient httpclient) {
        if (keyStorePath != null) {
            try {
                installSSL(httpclient, keyStorePath, keyStorePwd);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public static void installSSL(DefaultHttpClient httpclient, String pathToKey, String pass)
            throws KeyStoreException, NoSuchAlgorithmException,
            UnrecoverableKeyException, KeyManagementException, IOException, CertificateException {

        KeyStore keystore = KeyStore.getInstance("jks");
        InputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(pathToKey));
            keystore.load(in, pass.toCharArray());
            KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmfactory.init(keystore, pass.toCharArray());

            SSLSocketFactory socketFactory = new SSLSocketFactory(keystore);
            Scheme sch = new Scheme("https", 443, socketFactory);
            httpclient.getConnectionManager().getSchemeRegistry().register(sch);

        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public static int downloadResource(DefaultHttpClient client, URI uri, String toFile) {
        try {
            HttpResponse response = client.execute(new HttpGet(uri));

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                byte[] bytes = EntityUtils.toByteArray(response.getEntity());
                saveToFile(toFile, bytes);
                return bytes.length;
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }

        return 0;
    }


    public static URI encode(String scheme, String host, Integer port, String path) throws URISyntaxException {
        return new URI(scheme, null, host, port != null ? port : -1, path, null, null);
    }


    public static void saveToFile(String filename, byte[] bytes) throws IOException {
        File parent = new File(filename).getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(filename);
            out.write(bytes);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                log.error(getStackTrace(e));
            }
        }
    }


    public static void loadProperties(Properties props, String propsFile) {
        System.out.println("Loading props from " + propsFile + "...");
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(propsFile));
            props.load(reader);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException e) {
                    log.error(getStackTrace(e));
                }
        }
    }

    public static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        t.printStackTrace(pw);
        pw.flush();
        sw.flush();
        return sw.toString();
    }


    public static void main(String[] args) throws IOException {
        String configFile = DEFAULT_CONFIG;

        Properties props = new Properties();
        loadProperties(props, configFile);

//        DOMConfigurator.configure(props.getProperty(LOG4J_CONFIG, DEFAULT_LOG4J));

        Updater updater = new Updater(props, null);
        updater.checkNewVersions();
        updater.harvestChanges();
        updater.downloadChanges();

    }

}
