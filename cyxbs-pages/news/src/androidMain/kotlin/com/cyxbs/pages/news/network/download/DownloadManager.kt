package com.cyxbs.pages.news.network.download

import android.os.Environment
import com.cyxbs.pages.news.network.ApiService
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.saveFile
import com.cyxbs.components.utils.network.getBaseUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit

/**
 * Author: Hosigus
 * Date: 2018/9/24 16:18
 * Description: 下载的入口
 */
object DownloadManager {

    fun download(listener: RedDownloadListener, id: String, fileName: String) {
        val client = OkHttpClient.Builder()
                .addNetworkInterceptor(RedDownloadInterceptor(listener))
                .build()
        listener.onDownloadStart()

        Retrofit.Builder()
                .baseUrl(getBaseUrl())
                .client(client)
                .build()
                .create(ApiService::class.java)
                .download(id)
                .enqueue(object : retrofit2.Callback<ResponseBody> {
                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                        listener.onFail(t)
                    }

                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                        val body = response.body() ?: return
                        val state = Environment.getExternalStorageState()
                        if (Environment.MEDIA_MOUNTED != state && Environment.MEDIA_MOUNTED_READ_ONLY != state) {
                            listener.onFail(Exception("permission deny"))
                            return
                        }
                        try {
                            val uri = com.cyxbs.components.init.appContext.saveFile(body.byteStream().readBytes(),"$fileName.${splitFileType(response.headers()["Content-Disposition"])}")
                          println("AAAAA ${uri?.path}")
                          listener.onSuccess(uri)
                        } catch (e: Exception) {
                            listener.onFail(e)
                        }
                    }
                })
    }

    private fun splitFileType(string: String?) = string?.let {
        it.substring(it.indexOf("filename="), it.length).substringAfterLast(".")
    }

}