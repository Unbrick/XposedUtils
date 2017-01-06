package git.unbrick.xposedhelpers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;
import static de.robv.android.xposed.XposedHelpers.getSurroundingThis;
import static git.unbrick.xposedhelpers.XposedUtils.Helper.getActivityFromActivityThread;
import static git.unbrick.xposedhelpers.XposedUtils.Helper.getSystemContext;


@SuppressWarnings({"WeakerAccess", "unused"})
public class XposedUtils {
    private static final String TAG = XposedUtils.class.getSimpleName();
    private static boolean exceptionHandeled;
    private static XposedUtils instance;
    private String baseurl;
    private static XC_LoadPackage.LoadPackageParam lpparam;

    private XposedUtils() {/*empty method to prevent instantiation*/}

    private XposedUtils(String baseurl, XC_LoadPackage.LoadPackageParam lpparam, boolean disableAnalytics) {
        this.baseurl = baseurl;
        XposedUtils.lpparam = lpparam;

        installUncaughtExceptionHandler();

        if (disableAnalytics)
            disableAnalytics();
    }

    private void installUncaughtExceptionHandler() {
        try {
            findAndHookMethod(ThreadGroup.class, "uncaughtException", Thread.class, Throwable.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    handleException((Throwable) param.args[1]);
                }
            });
        }catch (Throwable ignored){
            Log.e(TAG,"Failed installing UncaughtExceptionHandler!");
        }
    }

    private PackageInfo getPackageInfo() {
        PackageInfo info = null;
        try {
            info = getSystemContext().getPackageManager().getPackageInfo(lpparam.packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return info != null ? info : null;
    }

    public static XposedUtils get() {
        if (instance == null)
            throw new RuntimeException("Please use the " + XposedUtils.class.getSimpleName() + ".Builder to create a instance before accessing it.");
        else
            return instance;
    }

    private void disableAnalytics() {
        if (findClassIfExists("com.crashlytics.android.core.CrashlyticsCore.Builder", lpparam.classLoader) != null) {
            Log.d(TAG, "Found Crashlytics, trying to disable it.");
            try {
                findAndHookMethod("com.crashlytics.android.core.CrashlyticsCore.Builder", lpparam.classLoader, "build", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        callMethod(param.thisObject, "disabled", true);
                    }
                });
                Log.d(TAG, "Crashlytics successfully disabled!");
            } catch (Throwable ignored) {
                Log.e(TAG, "Failed disabling Crashlytics, something went wrong :(");
            }
        }


        if (findClassIfExists("com.crashlytics.android.ndk.JniNativeApi", lpparam.classLoader) != null) {
            Log.d(TAG, "Found CrashlyticsJniNativeApi, trying to disable it.");
            try {
                findAndHookMethod("com.crashlytics.android.ndk.JniNativeApi", lpparam.classLoader, "initialize", String.class, AssetManager.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        return false;
                    }
                });
                Log.d(TAG, "CrashlyticsJniNativeApi successfully disabled!");
            } catch (Throwable ignored) {
                Log.e(TAG, "Failed disabling CrashlyticsJniNativeApi, something went wrong :(");
            }
        }

        if (findClassIfExists("com.rubylight.android.statistics.impl.TrackerImpl", lpparam.classLoader) != null) {
            Log.d(TAG, "Found RubylightAnalytics, trying to disable it.");
            try {
                findAndHookMethod("com.rubylight.android.statistics.impl.TrackerImpl", lpparam.classLoader, "k", Map.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        return null;
                    }
                });
                Log.d(TAG, "RubylightAnalytics successfully disabled!");
            } catch (Throwable ignored) {
                Log.e(TAG, "Failed disabling RubylightAnalytics, something went wrong :(");
            }
        }

    }

    public void showBugReportDialog() {
        final Context ctx = getActivityFromActivityThread();
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(ctx)
                .setTitle("Report a bug")
                .setMessage("Please give us a brief description of the bug.");
        final EditText input = new EditText(ctx);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        layoutParams.setMargins(20,10,20,10);

        input.setLayoutParams(layoutParams);
        alertDialog.setView(input);

        alertDialog.setPositiveButton("Send", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                XposedUtils.get().reportBug(input.getText().toString());
                Toast.makeText(ctx,"Sent!", Toast.LENGTH_LONG).show();
            }
        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });

        alertDialog.show();
    }

    public void reportBug(String description) {
        PackageInfo info = getPackageInfo();

        String logcat = getLogCat();

        int xposedversion = XposedBridge.getXposedVersion();
        int sdkversion = android.os.Build.VERSION.SDK_INT;


        String hookingAppVersionName = BuildConfig.VERSION_NAME;
        String hookedAppVersionName = info != null ? info.versionName : null;

        int hookingAppVersionCode = BuildConfig.VERSION_CODE;
        int hookedAppVersionCode = info != null ? info.versionCode : 0;


        RetrofitProvider.getApi(baseurl).postBugReport(
                lpparam.packageName,
                description,
                xposedversion,
                sdkversion,
                logcat,
                hookedAppVersionName,
                hookedAppVersionCode,
                hookingAppVersionName,
                hookingAppVersionCode
        ).enqueue(new EmptyCallback<JSONObject>());
    }

    private void handleException(Throwable arg) {
        if (!exceptionHandeled) {
            exceptionHandeled = true;
            PackageInfo info = getPackageInfo();

            String stacktrace = throwableToString(arg);
            String logcat = getLogCat();

            int xposedversion = XposedBridge.getXposedVersion();
            int sdkversion = android.os.Build.VERSION.SDK_INT;


            String hookingAppVersionName = BuildConfig.VERSION_NAME;
            String hookedAppVersionName = info != null ? info.versionName : null;

            int hookingAppVersionCode = BuildConfig.VERSION_CODE;
            int hookedAppVersionCode = info != null ? info.versionCode : 0;


            RetrofitProvider.getApi(baseurl).postStackTrace(
                    lpparam.packageName,
                    stacktrace,
                    xposedversion,
                    sdkversion,
                    logcat,
                    hookedAppVersionName,
                    hookedAppVersionCode,
                    hookingAppVersionName,
                    hookingAppVersionCode
            ).enqueue(new EmptyCallback<JSONObject>());
        }
    }

    private String throwableToString(Throwable thr) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        thr.printStackTrace(pw);
        return sw.toString();
    }

    private String getLogCat() {
        Process logcat;
        final StringBuilder log = new StringBuilder();
        try {
            logcat = Runtime.getRuntime().exec(new String[]{"logcat", "-d"});
            BufferedReader br = new BufferedReader(new InputStreamReader(logcat.getInputStream()), 4 * 1024);
            String line;
            String separator = System.getProperty("line.separator");
            while ((line = br.readLine()) != null) {
                log.append(line);
                log.append(separator);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return log.toString();
    }

    public static class Helper {
        public static Context getSystemContext() {
            return (Context) callMethod(callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread"), "getSystemContext");
        }

        public static Context getApplicationContext(){
            try {
                return getSystemContext().createPackageContext(lpparam.packageName, Context.CONTEXT_IGNORE_SECURITY);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            Log.e(TAG,"Coudnt find Application you are trying to create a context for. Returning the system context.");
            return getSystemContext();
        }

        @Deprecated
        public static Activity getActivityByParam(XC_MethodHook.MethodHookParam param) {
            Object currentObject = param.thisObject;
            Activity ac;
            try {
                ac = (Activity) callMethod(param.thisObject, "getActivity");
                while (ac == null) {
                    currentObject = getSurroundingThis(currentObject);
                    ac = (Activity) callMethod(currentObject, "getActivity");
                }
            } catch (Throwable th) {
                throw new RuntimeException("Cant get a activity from here!");
            }
            return ac;
        }

        public static Activity getActivityFromActivityThread() {
            try {
                Class activityThreadClass = Class.forName("android.app.ActivityThread");
                Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
                Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
                activitiesField.setAccessible(true);

                Map<Object, Object> activities = (Map<Object, Object>) activitiesField.get(activityThread);
                if(activities == null)
                    return null;

                for (Object activityRecord : activities.values()) {
                    Class activityRecordClass = activityRecord.getClass();
                    Field pausedField = activityRecordClass.getDeclaredField("paused");
                    pausedField.setAccessible(true);
                    if (!pausedField.getBoolean(activityRecord)) {
                        Field activityField = activityRecordClass.getDeclaredField("activity");
                        activityField.setAccessible(true);
                        return (Activity) activityField.get(activityRecord);
                    }
                }
            }catch (Throwable th){
                th.printStackTrace();
                Log.e(TAG,"CANNOT FIND CURRENT ACTIVITY!");
            }
            return null;
        }
    }

    public static class Builder {

        private boolean disableAnalytics;
        private String baseUrl;
        private XC_LoadPackage.LoadPackageParam lpparam;

        public Builder withBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder withLoadPackageParam(XC_LoadPackage.LoadPackageParam lpparam) {
            this.lpparam = lpparam;
            return this;
        }

        public XposedUtils build() {
            instance = new XposedUtils(baseUrl, lpparam, disableAnalytics);
            return instance;
        }

        public Builder disableAnalytics(boolean disable) {
            this.disableAnalytics = disable;
            return this;
        }

    }

    private class EmptyCallback<T> implements Callback<T> {
        @Override
        public void onResponse(Call<T> call, Response<T> response) {
        }

        @Override
        public void onFailure(Call<T> call, Throwable t) {
        }
    }
}
