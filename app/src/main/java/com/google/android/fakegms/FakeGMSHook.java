/*
 * SPDX-FileCopyrightText: 2021, nnuudev
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.fakegms;

import android.content.Context;

import com.google.android.gms.common.security.ProviderInstallerImpl;

import java.util.HashSet;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class FakeGMSHook implements IXposedHookLoadPackage {

    private static final HashSet<String> WHITELIST = new HashSet<>();
    static {
        WHITELIST.add("com.shazam.android");
        WHITELIST.add("bluehorse.clover");
        // add app package names to this list to enable the module for them
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        if (!WHITELIST.contains(lpparam.packageName))
            return;

        XposedBridge.log("FakeGMS: package loaded: " + lpparam.packageName);
        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader,
                "attach", Context.class,
                new XC_MethodHook() {

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("FakeGMS: getting context of " + lpparam.packageName + "...");
                        Context context = (Context) param.args[0];
                        ProviderInstallerImpl.insertProvider(context);
                        XposedBridge.log("FakeGMS: provider inserted for " + lpparam.packageName);
                    }

                });

    }

    /* ***************************** */
    /* * Rainbow Dash is best pony * */
    /* ***************************** */
}