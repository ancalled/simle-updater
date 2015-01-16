package com.github.ancalled;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class Version {

    private final int majorVersion;
    private final int minorVersion;
    private final int buildNumber;
    private final Date buildTime;
    private final String appVersion;


    public Version(int majorVersion, int minorVersion,
                   int buildNumber, Date buildTime, String appVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.buildNumber = buildNumber;
        this.buildTime = buildTime;
        this.appVersion = appVersion;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public int getBuildNumber() {
        return buildNumber;
    }

    public Date getBuildTime() {
        return buildTime;
    }

    public String getAppVersion() {
        return appVersion;
    }

    @Override
    public String toString() {
        return "Major-Version: " + majorVersion + "\n" +
                "Minor-Version: " + minorVersion + "\n" +
                "Build-Number: " + buildNumber + "\n" +
                "Build-Time: " + buildTime + "\n" +
                "TS-Version: " + appVersion;
    }

    public static final DateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static Version getVersion(String appJarPath) {
        Manifest manifest = null;
        try {   //todo make manifest search for OS X

//            if (!System.getProperty("os.name").startsWith("Windows")) {
//            Class clazz = Main.class;
//            String className = clazz.getSimpleName() + ".class";
//            String classPath = clazz.getResource(className).toString();
//            if (!classPath.startsWith("jar")) {
//                return null;
//            }
//            String appJarPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) +
//                    "/META-INF/MANIFEST.MF";
//            manifest = new Manifest(new URL(appJarPath).openStream());
//            } else {
//
//                InputStream manifestStream = Thread.currentThread().
//                        getContextClassLoader().getResourceAsStream(appJarPath);
            JarFile jarfile = new JarFile(appJarPath);

            manifest = jarfile.getManifest();

//                manifest = new Manifest(manifestStream);
//            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        if (manifest != null) {
            Attributes attributes = manifest.getMainAttributes();
            String majVerStr = attributes.getValue("Major-Version");
            String minVerStr = attributes.getValue("Minor-Version");
            String buildNoStr = attributes.getValue("Build-Number");
            String buildTimeStr = attributes.getValue("Build-Time");
            String appVer = attributes.getValue("App-Version");

            int majVerson = majVerStr != null && !"".equals(majVerStr) ?
                    Integer.parseInt(majVerStr) : 0;
            int minVerson = minVerStr != null && !"".equals(minVerStr) ?
                    Integer.parseInt(minVerStr) : 0;
            int buildNumber = buildNoStr != null && !"".equals(buildNoStr) ?
                    Integer.parseInt(buildNoStr) : 0;
            Date buildTime = buildTimeStr != null && !"".equals(buildTimeStr) ?
                    parseDate(buildTimeStr) : null;

            return new Version(majVerson, minVerson, buildNumber, buildTime, appVer);
        }

        return null;
    }

    public static Date parseDate(String dateStr) {
        if (dateStr == null) {
            return null;
        }

        try {
            return FORMAT.parse(dateStr);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }
}
