package release.net

import com.google.gson.annotations.SerializedName
import Config

/**
 * ...
 * @author RQ527 (Ran Sixiang)
 * @email 1799796122@qq.com
 * @date 2023/11/11
 * @Description:
 */
data class ReleaseData(
  @SerializedName("apk_url")
    val apkUrl: String,
  @SerializedName("update_content")
    val updateContent: String,
  @SerializedName("version_code")
    val versionCode: Int,
  @SerializedName("version_name")
    val versionName: String
)

data class ApkUploadData(
    @SerializedName("data")
    val data: String,
    @SerializedName("ok")
    val ok: Boolean
)
