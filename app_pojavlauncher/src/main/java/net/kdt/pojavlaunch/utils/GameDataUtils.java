package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Helpers for moving the Minecraft data directory outside the app's private storage.
 */
public final class GameDataUtils {
    private GameDataUtils() {}

    public static boolean hasStorageAccess(Context context) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        if (Build.VERSION.SDK_INT >= 23) {
            return context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static void openAllFilesAccessSettings(Activity activity) {
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName())));
        } catch (ActivityNotFoundException e) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (ActivityNotFoundException ignored) {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + activity.getPackageName())));
            }
        }
    }

    /**
     * Returns true if both paths are the same, or if child is inside parent.
     */
    public static boolean isSameOrInside(File parent, File child) {
        try {
            String parentPath = parent.getCanonicalPath();
            String childPath = child.getCanonicalPath();
            return childPath.equals(parentPath) || childPath.startsWith(parentPath + File.separator);
        } catch (IOException e) {
            String parentPath = parent.getAbsoluteFile().getAbsolutePath();
            String childPath = child.getAbsoluteFile().getAbsolutePath();
            return childPath.equals(parentPath) || childPath.startsWith(parentPath + File.separator);
        }
    }

    public static boolean isDirectoryEmpty(File dir) {
        File[] files = dir.listFiles();
        return files != null && files.length == 0;
    }

    public static long getDirectorySize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        if (dir.isFile()) return dir.length();
        File[] children = dir.listFiles();
        if (children == null) return 0;
        long total = 0;
        for (File child : children) {
            if (shouldSkip(child)) continue;
            total += getDirectorySize(child);
        }
        return total;
    }

    public static void copyGameData(File source, File destination, ProgressListener listener) throws IOException {
        long totalBytes = getDirectorySize(source);
        long usableSpace = destination.getUsableSpace();
        if (usableSpace > 0 && usableSpace < totalBytes) {
            throw new IOException("Not enough free space in destination (need " + totalBytes + " bytes)");
        }
        copyDirectory(source, destination, listener, new long[]{0, totalBytes});
    }

    private static void copyDirectory(File sourceDir, File destinationDir,
                                      ProgressListener listener, long[] progress) throws IOException {
        File[] children = sourceDir.listFiles();
        if (children == null) throw new IOException("Cannot list " + sourceDir.getAbsolutePath());
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw new IOException("Cannot create " + destinationDir.getAbsolutePath());
        }
        for (File child : children) {
            if (shouldSkip(child)) continue;
            File target = new File(destinationDir, child.getName());
            if (child.isDirectory()) {
                copyDirectory(child, target, listener, progress);
            } else if (child.isFile()) {
                copyFile(child, target, listener, progress);
            }
        }
    }

    private static void copyFile(File source, File target,
                                 ProgressListener listener, long[] progress) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent.getAbsolutePath());
        }
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                progress[0] += read;
                if (listener != null) listener.onProgress(source, progress[0], progress[1]);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        target.setLastModified(source.lastModified());
    }

    private static boolean shouldSkip(File file) {
        String name = file.getName();
        if ("session.lock".equals(name)) return true;
        if ("latestlog.txt".equals(name) || "latestcrash.txt".equals(name)) return true;
        if (!file.isDirectory()) return false;
        return "logs".equals(name)
                || "crash-reports".equals(name)
                || "lwjgl3".equals(name)
                || name.startsWith("caciocavallo");
    }

    public interface ProgressListener {
        void onProgress(File currentFile, long copiedBytes, long totalBytes);
    }
}
