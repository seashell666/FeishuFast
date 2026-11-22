package de.robv.android.xposed;

import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Compile-time stub of the Xposed API (api 82).
 * The real implementation is provided by the framework (Vector / LSPosed) at runtime.
 * This stub is excluded from the APK.
 */
public interface IXposedHookLoadPackage {
    void handleLoadPackage(LoadPackageParam lpparam) throws Throwable;
}
