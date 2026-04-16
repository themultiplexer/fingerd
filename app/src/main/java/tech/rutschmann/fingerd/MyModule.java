package tech.rutschmann.fingerd;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;
import java.util.ArrayList;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MyModule implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log("Loaded in: " + lpparam.packageName);
        //hookFingerprintManager(lpparam);
        //hookInternal(lpparam);
        hookProvider(lpparam);
        hookLockscreen(lpparam);
    }

    private void hookProvider(final XC_LoadPackage.LoadPackageParam lpparam) {
/*
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().contains("onAuthenticated")) {
                XposedBridge.log("Method candidate: " + m.toString());
            }
        }
*/


        try {

            /*
            XposedHelpers.findAndHookMethod(
                "com.android.server.biometrics.sensors.fingerprint.aidl.FingerprintAuthenticationClient",
                lpparam.classLoader,
                "onAuthenticated",
                "android.hardware.biometrics.BiometricAuthenticator$Identifier",
                boolean.class,   // fingerId
                ArrayList.class, // token
                new XC_MethodHook() {

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {

                        XposedBridge.log("Biometric SUCCESS");
                    }
                }
        );
            * */
            Class<?> fingerprintService = XposedHelpers.findClass(
                    "com.android.server.biometrics.sensors.fingerprint.aidl.FingerprintAuthenticationClient",
                    lpparam.classLoader
            );

            for (Method m : fingerprintService.getDeclaredMethods()) {
                if (m.getName().contains("onAuthenticated")) {
                    XposedBridge.log("Method candidate: " + m.toString());
                }
            }

            Class<?> identifierClass = XposedHelpers.findClass(
                    "android.hardware.biometrics.BiometricAuthenticator$Identifier",
                    lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                    fingerprintService,
                    "onAuthenticated",
                    identifierClass,
                    boolean.class,   // fingerId
                    ArrayList.class, // token
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            XposedBridge.log(
                                    "Fingerprint authenticated!"
                            );
                            Object identifier = param.args[0];
                            boolean authenticated = (boolean) param.args[1];

                            if (!authenticated) return;

                            Object client = param.thisObject;
                            String owner = (String) XposedHelpers.callMethod(client, "getOwnerString");

                            int biometricsId = (int) XposedHelpers.callMethod(identifier, "getBiometricId");
                            CharSequence name = (CharSequence) XposedHelpers.callMethod(identifier, "getName");

                            boolean isKeyguard = owner.contains("Keyguard") || owner.contains("systemui");

                            if (isKeyguard) {
                                XposedBridge.log("Lockscreen fingerd!");
                                if (biometricsId == 2111758441) {
                                    Context context = (Context) XposedHelpers.callMethod(param.thisObject, "getContext");
                                    Intent intent = context.getPackageManager().getLaunchIntentForPackage("org.codeaurora.snapcam");
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    new Handler(Looper.getMainLooper()).postDelayed(
                                            () -> context.startActivity(intent),
                                            250
                                    );
                                }
                            } else {
                                XposedBridge.log("NOT LS fingerd!");

                            }

                            XposedBridge.log("Fingerprint matched: id=" + name.toString() + " user=" + biometricsId);

                            LastFingerprintStore.lastFingerId = 0;
                        }
                    }
            );

        } catch (Throwable t) {
            XposedBridge.log("FingerprintService hook failed: " + t);
        }
    }

    private void hookLockscreen(final XC_LoadPackage.LoadPackageParam lpparam) {

        Class<?> cls = XposedHelpers.findClass(
                    "com.android.keyguard.KeyguardUpdateMonitor",
                lpparam.classLoader
        );

        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().contains("onAuthenticated")) {
                XposedBridge.log("Method candidate: " + m.toString());
            }
        }

        XposedHelpers.findAndHookMethod( cls, "onFingerprintAuthenticated",
        int.class, boolean.class,
                new XC_MethodHook() {

                @Override
                protected void afterHookedMethod (MethodHookParam param){
                    int userId = (int) param.args[0];
                    boolean smth = (boolean) param.args[1];

                    XposedBridge.log(
                            "Lockscreen biometric unlock: user=" + userId + " smth=" + smth
                    );

                    int fingerId = LastFingerprintStore.lastFingerId;

                    XposedBridge.log("Lockscreen unlocked with fingerId=" + fingerId);
                }
            }
        );
    }

    private void hookInternal(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> biometricService = XposedHelpers.findClass(
                    "com.android.server.biometrics.BiometricService",
                    lpparam.classLoader
            );

            for (Method m : biometricService.getDeclaredMethods()) {
                if (m.getName().contains("AuthenticationSucceeded")) {
                    XposedBridge.log("Method candidate: " + m.toString());
                }
            }

            XposedHelpers.findAndHookMethod(
                    biometricService,
                    "handleAuthenticationSucceeded",
                    long.class,
                    int.class,
                    byte[].class,
                    new XC_MethodHook() {

                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {

                            long requestId = (long) param.args[0];
                            int sensorId = (int) param.args[1];
                            byte[] token = (byte[]) param.args[2];

                            XposedBridge.log(
                                    "Biometric success: requestId=" +
                                            requestId +
                                            " sensorId=" +
                                            sensorId +
                                            " tokenSize=" +
                                            (token != null ? token.length : 0)
                            );
                        }
                    }
            );

            XposedBridge.log("BiometricService hook installed");

        } catch (Throwable t) {
            XposedBridge.log("BiometricService hook failed: " + t);
        }
    }


    private void hookFingerprintManager(final XC_LoadPackage.LoadPackageParam lpparam) {

        try {

            XposedHelpers.findAndHookMethod(
                    "android.hardware.fingerprint.FingerprintManager$AuthenticationCallback",
                    lpparam.classLoader,
                    "onAuthenticationSucceeded",
                    "android.hardware.fingerprint.FingerprintManager$AuthenticationResult",
                    new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {

                            XposedBridge.log("Fingerprint SUCCESS");
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "android.hardware.fingerprint.FingerprintManager$AuthenticationCallback",
                    lpparam.classLoader,
                    "onAuthenticationFailed",
                    new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {

                            XposedBridge.log("Fingerprint FAILED");
                        }
                    }
            );

        } catch (Throwable t) {
            XposedBridge.log("FingerprintManager hook failed: " + t);
        }
    }


    private void hookBiometricPrompt(final XC_LoadPackage.LoadPackageParam lpparam) {

        try {

            XposedHelpers.findAndHookMethod(
                    "android.hardware.biometrics.BiometricPrompt$AuthenticationCallback",
                    lpparam.classLoader,
                    "onAuthenticationSucceeded",
                    "android.hardware.biometrics.BiometricPrompt$AuthenticationResult",
                    new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {

                            XposedBridge.log("Biometric SUCCESS");
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "android.hardware.biometrics.BiometricPrompt$AuthenticationCallback",
                    lpparam.classLoader,
                    "onAuthenticationFailed",
                    new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {

                            XposedBridge.log("Biometric FAILED");
                        }
                    }
            );

        } catch (Throwable t) {
            XposedBridge.log("BiometricPrompt hook failed: " + t);
        }
    }
}
