package git.unbrick.xposedhelpers;

import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

interface XposedHelpersApi {
    @FormUrlEncoded
    @POST("/api/crash")
    Call<JSONObject> postStackTrace(@Field("package") String pkgname,
                                    @Field("stacktrace") String stacktrace,
                                    @Field("xposed_version") int version,
                                    @Field("sdk_version") int sdkversion,
                                    @Field("logcat") String logcat,
                                    @Field("hookedAVN") String hookedAppVersionName,
                                    @Field("hookedAVC") int hookedAppVersionCode,
                                    @Field("hookingAVN") String hookingAppVersionName,
                                    @Field("hookingAVC") int hookingAppVersionCode);

    @FormUrlEncoded
    @POST("/api/bug")
    Call<JSONObject> postBugReport(@Field("package") String pkgname,
                                    @Field("description") String description,
                                    @Field("xposed_version") int version,
                                    @Field("sdk_version") int sdkversion,
                                    @Field("logcat") String logcat,
                                    @Field("hookedAVN") String hookedAppVersionName,
                                    @Field("hookedAVC") int hookedAppVersionCode,
                                    @Field("hookingAVN") String hookingAppVersionName,
                                    @Field("hookingAVC") int hookingAppVersionCode);
}
