package git.unbrick.xposedhelpers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by Admin on 03.01.2017.
 */

public class RetrofitProvider {
    private static XposedHelpersApi api;

    public static XposedHelpersApi getApi(String baseurl){
        if (api==null){
            api = new Retrofit.Builder()
                    .baseUrl(baseurl)
                    .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setLenient().create()))
                    .build()
                    .create(XposedHelpersApi.class);
        }
        return api;
    }
}
