# ![](https://raw.githubusercontent.com/nnuudev/FakeGMS/master/app/src/main/res/mipmap-mdpi/ic_launcher.png) FakeGMS

FakeGMS is a Xposed module to inject an updated version of Conscrypt into selected apps, providing GmsCore_OpenSSL to old devices without a recent version of Google Play Services.

Background
----------

Android 4.4 was shipped with TLSv1.2 disabled by default and without support for TLSv1.3. Currently (2021), many servers no longer support the old and now deprecated TLSv1.1, using one of the newer alternatives instead, thus becoming inaccessible for outdated devices. As a workaround, apps that are meant to work on those old devices started relying on the libraries provided by Google Play Services, usually including some code like the following:

    Context gms = App.getContext()
        .createPackageContext("com.google.android.gms",
        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
    gms.getClassLoader()
        .loadClass("com.google.android.gms.common.security.ProviderInstallerImpl")
        .getMethod("insertProvider", Context.class).invoke(null, gms);

Unfortunately, this workaround doesn't work when the version of Google Play Services on the device is too old, and the app will still be unable to connect to Internet. Due to the huge size of a recent version of Google Play Services, updating is not always an option on devices with limited space.

The goal of this module is to provide *only* the bare minimum required to update the security provider, using only about 1/100th the space that would be required for a full update of Google Play Services.


Implementation details
----------------------

Most of the code in `ProviderInstallerImpl` was copied from the same name class from [microG GmsCore](https://github.com/microg/GmsCore) and it's licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0). The code was modified to use the original [Conscrypt](https://github.com/google/conscrypt) library rather than [the microG fork](https://github.com/microg/conscrypt-gmscore), since devices with an old version of Google Play Services will block the load of an external `libconscrypt_gmscore_jni` library.

Because of the overhead caused by installing a security provider, the module uses a whitelist approach, and the provider will only be injected in the specified apps. This whitelist is currently hardcoded and limited to Shazam (for its use with Shazam 8.4.2-190521) and [Blue Clover](https://github.com/nnuudev/BlueClover) (for internal testing).


Installation
-----

The module requires a **rooted** Android 4.4 device with [Xposed Installer](https://repo.xposed.info/module/de.robv.android.xposed.installer) properly installed and enabled.

For the module to work, it's **necessary** to copy manually the library `libconscrypt_jni.so` to `/system/lib`. This library can be extracted from the .apk of the module after compiling it, or from the .aar file of the version of Conscrypt specified in build.gradle, currently  [conscrypt-android-2.5.2.aar](https://repo1.maven.org/maven2/org/conscrypt/conscrypt-android/2.5.2/conscrypt-android-2.5.2.aar). It's obviously necessary to use specifically the file corresponding to the architecture of the device (probably `armeabi-v7a`). It's advised to use a root-enabled file manager such as ES File Explorer 4.0.2.3 to copy the file to the right folder and set its permissions to 644.

It's recommended to build and launch the module from Android Studio with the device plugged in, this way the installed .apk will include only the necessary files from the right architecture, rather than all the available variants.


License
-------

This module is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).<br>
Part of its code was edited from [microG Services Core (GmsCore)](https://github.com/microg/GmsCore).

    Copyright 2013-2021 microG Project Team

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.