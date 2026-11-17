package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Environment;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.kdt.pickafile.FileListView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;

/**
 * Shared folder picker for the Minecraft data directory.
 */
public final class GameDataPicker {
    private GameDataPicker() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (!GameDataUtils.hasStorageAccess(activity)) return;

        FrameLayout container = new FrameLayout(activity);
        int padding = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        int listHeight = Math.round(400 * activity.getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);

        FileListView fileListView = new FileListView(activity);
        fileListView.setShowFiles(false);
        fileListView.setShowFolders(true);
        container.addView(fileListView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, listHeight));
        fileListView.lockPathAt(new File("/storage"));

        File startDir = Environment.getExternalStorageDirectory();
        if (startDir == null || !startDir.exists()) startDir = new File(Tools.DIR_GAME_HOME);
        fileListView.listFileAt(startDir);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.game_home_dialog_title)
                .setView(container)
                .setPositiveButton(R.string.game_home_select, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.game_home_default, null)
                .create();
        fileListView.setDialogTitleListener(dialog::setTitle);
        fileListView.refreshPath();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                File selected = fileListView.getFullPath();
                if (selected == null) return;
                if (selected.getName().equals(".minecraft")) {
                    Toast.makeText(activity, R.string.game_home_select_parent, Toast.LENGTH_LONG).show();
                    return;
                }
                if (!FileUtils.ensureDirectorySilently(selected)) {
                    Toast.makeText(activity, R.string.game_home_invalid, Toast.LENGTH_LONG).show();
                    return;
                }
                dialog.dismiss();
                applyGameHome(activity, selected, false);
            });
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.dismiss();
                File defaultRoot = Tools.getDefaultStorageRoot(activity);
                if (defaultRoot == null) {
                    Toast.makeText(activity, R.string.storage_required, Toast.LENGTH_LONG).show();
                    return;
                }
                applyGameHome(activity, defaultRoot, true);
            });
        });
        dialog.show();
    }

    private static void applyGameHome(Activity activity, File newRoot, boolean useDefault) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (newRoot.getName().equals(".minecraft")) {
            Toast.makeText(activity, R.string.game_home_select_parent, Toast.LENGTH_LONG).show();
            return;
        }
        File oldRoot = new File(Tools.DIR_GAME_HOME);
        if (isSamePath(oldRoot, newRoot)) {
            Toast.makeText(activity, R.string.game_home_same, Toast.LENGTH_SHORT).show();
            return;
        }
        if (GameDataUtils.isSameOrInside(oldRoot, newRoot)
                || GameDataUtils.isSameOrInside(newRoot, oldRoot)) {
            Toast.makeText(activity, R.string.game_home_nested, Toast.LENGTH_LONG).show();
            return;
        }
        if (!FileUtils.ensureDirectorySilently(newRoot)) {
            Toast.makeText(activity, R.string.game_home_invalid, Toast.LENGTH_LONG).show();
            return;
        }

        if (new File(newRoot, ".minecraft").exists() || !GameDataUtils.isDirectoryEmpty(newRoot)) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.game_home_not_empty_title)
                    .setMessage(R.string.game_home_not_empty_message)
                    .setPositiveButton(R.string.game_home_use_folder,
                            (dialog, which) -> commitGameHome(activity, newRoot, useDefault))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.game_home_copy_title)
                .setMessage(R.string.game_home_copy_message)
                .setPositiveButton(R.string.game_home_copy_start,
                        (dialog, which) -> commitGameHome(activity, newRoot, useDefault, true))
                .setNegativeButton(R.string.game_home_copy_skip,
                        (dialog, which) -> commitGameHome(activity, newRoot, useDefault))
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    private static void commitGameHome(Activity activity, File newRoot, boolean useDefault) {
        saveGameHome(newRoot, useDefault);
        Toast.makeText(activity, R.string.game_home_restarting, Toast.LENGTH_SHORT).show();
        Tools.restartLauncher(activity.getApplicationContext());
    }

    private static void commitGameHome(Activity activity, File newRoot, boolean useDefault,
                                       boolean copyOldData) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        Context appContext = activity.getApplicationContext();
        File oldRoot = new File(Tools.DIR_GAME_HOME);

        ProgressDialog progress = new ProgressDialog(activity);
        progress.setMessage(activity.getString(R.string.game_home_copying));
        progress.setCancelable(false);
        progress.setIndeterminate(true);
        progress.show();

        PojavApplication.sExecutorService.execute(() -> {
            try {
                GameDataUtils.copyGameData(oldRoot, newRoot, null);
                Tools.MAIN_HANDLER.post(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    saveGameHome(newRoot, useDefault);
                    Toast.makeText(appContext, R.string.game_home_restarting, Toast.LENGTH_SHORT).show();
                    Tools.restartLauncher(appContext);
                });
            } catch (IOException e) {
                Tools.MAIN_HANDLER.post(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    Toast.makeText(appContext,
                            appContext.getString(R.string.game_home_copy_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static void saveGameHome(File newRoot, boolean useDefault) {
        if (LauncherPreferences.DEFAULT_PREF == null) return;
        SharedPreferences.Editor editor = LauncherPreferences.DEFAULT_PREF.edit();
        if (useDefault) {
            editor.remove(LauncherPreferences.PREF_KEY_GAME_HOME);
        } else {
            editor.putString(LauncherPreferences.PREF_KEY_GAME_HOME, newRoot.getAbsolutePath());
        }
        String defaultCtrl = LauncherPreferences.DEFAULT_PREF.getString("defaultCtrl", null);
        if (defaultCtrl != null) {
            try {
                String oldPath = new File(Tools.DIR_GAME_HOME).getCanonicalPath();
                String newPath = newRoot.getCanonicalPath();
                if (defaultCtrl.startsWith(oldPath + File.separator)) {
                    editor.putString("defaultCtrl", defaultCtrl.replace(oldPath, newPath));
                }
            } catch (IOException ignored) {}
        }
        editor.commit();
    }

    private static boolean isSamePath(File first, File second) {
        try {
            return first.getCanonicalPath().equals(second.getCanonicalPath());
        } catch (IOException e) {
            return first.getAbsolutePath().equals(second.getAbsolutePath());
        }
    }
}
