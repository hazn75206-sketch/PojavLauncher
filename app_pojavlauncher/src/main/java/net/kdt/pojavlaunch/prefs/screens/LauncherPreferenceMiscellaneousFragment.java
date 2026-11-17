package net.kdt.pojavlaunch.prefs.screens;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.GLInfoUtils;
import net.kdt.pojavlaunch.utils.GameDataPicker;
import net.kdt.pojavlaunch.utils.GameDataUtils;

public class LauncherPreferenceMiscellaneousFragment extends LauncherPreferenceFragment {
    private static final int REQUEST_STORAGE_PERMISSION = 0x53;
    private Preference mGameHomePreference;

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_misc);
        Preference driverPreference = requirePreference("zinkPreferSystemDriver");
        PackageManager packageManager = driverPreference.getContext().getPackageManager();
        boolean supportsTurnip = Tools.checkVulkanSupport(packageManager) && GLInfoUtils.getGlInfo().isAdreno();
        driverPreference.setVisible(supportsTurnip);

        mGameHomePreference = requirePreference("gameHome");
        updateGameHomeSummary();
        mGameHomePreference.setOnPreferenceClickListener(preference -> {
            if (!ensureStorageAccess()) return true;
            GameDataPicker.show(requireActivity());
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateGameHomeSummary();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (LauncherPreferences.PREF_KEY_GAME_HOME.equals(key) || "defaultCtrl".equals(key)) {
            // These values are applied by restarting the launcher process.
            return;
        }
        super.onSharedPreferenceChanged(preferences, key);
    }

    private void updateGameHomeSummary() {
        if (mGameHomePreference == null || Tools.DIR_GAME_HOME == null) return;
        mGameHomePreference.setSummary(getString(R.string.preference_game_home_current, Tools.DIR_GAME_HOME));
    }

    private boolean ensureStorageAccess() {
        if (GameDataUtils.hasStorageAccess(requireContext())) return true;
        if (Build.VERSION.SDK_INT >= 30) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.game_home_permission_title)
                    .setMessage(R.string.game_home_permission_message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) ->
                            GameDataUtils.openAllFilesAccessSettings(requireActivity()))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_STORAGE_PERMISSION) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            GameDataPicker.show(requireActivity());
        } else {
            Toast.makeText(requireContext(), R.string.toast_permission_denied, Toast.LENGTH_LONG).show();
        }
    }
}
